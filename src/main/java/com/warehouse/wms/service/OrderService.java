package com.warehouse.wms.service;

import com.warehouse.wms.dto.OrderDTO;
import com.warehouse.wms.dto.OrderRequestDTO;
import com.warehouse.wms.exception.InvalidStatusTransitionException;
import com.warehouse.wms.exception.ResourceNotFoundException;
import com.warehouse.wms.model.Item;
import com.warehouse.wms.model.OutboundLine;
import com.warehouse.wms.model.OutboundOrder;
import com.warehouse.wms.model.OutboundOrder.OrderStatus;
import com.warehouse.wms.model.Warehouse;
import com.warehouse.wms.repository.AllocationRepository;
import com.warehouse.wms.repository.ItemRepository;
import com.warehouse.wms.repository.OutboundOrderRepository;
import com.warehouse.wms.repository.WarehouseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class OrderService {

    // ON CONFLICT makes a redelivered order message, even a concurrent one, resolve to the order already stored
    private static final String INSERT_ORDER_SQL = """
            INSERT INTO outbound_orders (warehouse_id, external_ref, priority, carrier, service_level, ship_by,
                                         ship_to_name, ship_to_address)
            VALUES (?, ?, ?, ?, ?, ?, ?, CAST(? AS JSONB))
            ON CONFLICT (warehouse_id, external_ref) DO NOTHING
            RETURNING id
            """;

    private final OutboundOrderRepository orderRepository;
    private final AllocationRepository allocationRepository;
    private final WarehouseRepository warehouseRepository;
    private final ItemRepository itemRepository;
    private final AllocationService allocationService;
    private final TaskService taskService;
    private final OrderEvents orderEvents;
    private final JdbcTemplate jdbcTemplate;
    private final JsonMapper jsonMapper;

    /** The stored order, and whether this request created it (false for a duplicate externalOrderId). */
    public record OrderIntake(OrderDTO order, boolean created) {}

    public OrderIntake createOrder(OrderRequestDTO request) {
        log.info("Receiving order {} for warehouse {}", request.getExternalOrderId(), request.getWarehouseCode());

        Warehouse warehouse = warehouseRepository.findByCode(request.getWarehouseCode())
                .orElseThrow(() -> new IllegalArgumentException("Unknown warehouse code: " + request.getWarehouseCode()));
        Map<Integer, Item> itemsByLine = resolveLines(request.getLines());

        OrderRequestDTO.ShipTo shipTo = request.getShipTo();
        Optional<Long> insertedId = jdbcTemplate.queryForList(INSERT_ORDER_SQL, Long.class,
                warehouse.getId(), request.getExternalOrderId(),
                request.getPriority() != null ? request.getPriority() : 0,
                request.getCarrier(), request.getServiceLevel(), request.getShipBy(),
                shipTo != null ? shipTo.getName() : null,
                shipTo != null && shipTo.getAddress() != null ? jsonMapper.writeValueAsString(shipTo.getAddress()) : null)
                .stream().findFirst();

        if (insertedId.isEmpty()) {
            log.info("Order {} already exists; returning it", request.getExternalOrderId());
            OutboundOrder existing = orderRepository
                    .findByWarehouseIdAndExternalRef(warehouse.getId(), request.getExternalOrderId())
                    .orElseThrow();
            return new OrderIntake(convertToDTO(existing), false);
        }

        OutboundOrder order = orderRepository.findById(insertedId.get()).orElseThrow();
        for (OrderRequestDTO.Line requestLine : request.getLines()) {
            OutboundLine line = new OutboundLine();
            line.setOrder(order);
            line.setLineNo(requestLine.getLineNo());
            line.setItem(itemsByLine.get(requestLine.getLineNo()));
            line.setQtyOrdered(requestLine.getQty());
            order.getLines().add(line);
        }
        orderRepository.flush();
        orderEvents.write(order, OrderEvents.ACCEPTED);

        log.info("Order {} created with id: {}", request.getExternalOrderId(), order.getId());
        return new OrderIntake(convertToDTO(order), true);
    }

    @Transactional(readOnly = true)
    public OrderDTO getOrder(Long id) {
        log.info("Fetching order by id: {}", id);
        return convertToDTO(findOrder(id));
    }

    @Transactional(readOnly = true)
    public List<OrderDTO> getOrders(OrderStatus status) {
        log.info("Fetching orders with status: {}", status);
        return orderRepository.search(status).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public OrderDTO allocateOrder(Long id) {
        return convertToDTO(allocationService.allocate(id));
    }

    /** Hands an allocated order to the floor: one PICK task per allocation. */
    public OrderDTO releaseOrder(Long id) {
        log.info("Releasing order id: {}", id);
        OutboundOrder order = orderRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + id));
        transition(order, OrderStatus.RELEASED);

        taskService.createPickTasks(order);
        log.info("Order {} released", id);
        return convertToDTO(order);
    }

    public OrderDTO cancelOrder(Long id) {
        log.info("Cancelling order id: {}", id);
        OutboundOrder order = orderRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + id));
        transition(order, OrderStatus.CANCELLED);

        allocationService.releaseOpenAllocations(order);
        taskService.cancelOpenTasks(id);
        log.info("Order {} cancelled", id);
        return convertToDTO(order);
    }

    // The one place an order's status changes outside allocation; rejects moves the state machine does not allow
    private void transition(OutboundOrder order, OrderStatus next) {
        if (!order.getStatus().canTransitionTo(next)) {
            throw new InvalidStatusTransitionException(
                    "Cannot change order status from " + order.getStatus() + " to " + next);
        }
        order.setStatus(next);
    }

    private Map<Integer, Item> resolveLines(List<OrderRequestDTO.Line> lines) {
        Set<Integer> lineNumbers = new HashSet<>();
        for (OrderRequestDTO.Line line : lines) {
            if (!lineNumbers.add(line.getLineNo())) {
                throw new IllegalArgumentException("Duplicate line number: " + line.getLineNo());
            }
        }
        return lines.stream().collect(Collectors.toMap(OrderRequestDTO.Line::getLineNo,
                line -> itemRepository.findBySku(line.getSku())
                        .orElseThrow(() -> new IllegalArgumentException("Unknown SKU: " + line.getSku()))));
    }

    private OutboundOrder findOrder(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + id));
    }

    private OrderDTO convertToDTO(OutboundOrder order) {
        OrderDTO dto = new OrderDTO();
        dto.setId(order.getId());
        dto.setExternalOrderId(order.getExternalRef());
        dto.setWarehouseId(order.getWarehouse().getId());
        dto.setWarehouseCode(order.getWarehouse().getCode());
        dto.setStatus(order.getStatus());
        dto.setPriority(order.getPriority());
        dto.setCarrier(order.getCarrier());
        dto.setServiceLevel(order.getServiceLevel());
        dto.setShipBy(order.getShipBy());
        if (order.getShipToName() != null || order.getShipToAddress() != null) {
            dto.setShipTo(new OrderRequestDTO.ShipTo(order.getShipToName(), order.getShipToAddress() == null ? null
                    : jsonMapper.readValue(order.getShipToAddress(), new TypeReference<Map<String, Object>>() {})));
        }
        dto.setCreatedAt(order.getCreatedAt());
        dto.setShippedAt(order.getShippedAt());
        dto.setLines(order.getLines().stream()
                .map(line -> new OrderDTO.LineDTO(line.getLineNo(), line.getItem().getId(), line.getItem().getSku(),
                        line.getQtyOrdered(), line.getQtyAllocated(), line.getQtyPicked(), line.getQtyShipped()))
                .collect(Collectors.toList()));
        dto.setAllocations(allocationRepository.findByOrderId(order.getId()).stream()
                .map(allocation -> new OrderDTO.AllocationDTO(allocation.getId(), allocation.getLine().getLineNo(),
                        allocation.getInventoryId(), allocation.getQty(), allocation.getStatus()))
                .collect(Collectors.toList()));
        return dto;
    }
}
