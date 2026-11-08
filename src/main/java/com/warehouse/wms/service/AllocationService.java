package com.warehouse.wms.service;

import com.warehouse.wms.exception.InvalidStatusTransitionException;
import com.warehouse.wms.exception.ResourceNotFoundException;
import com.warehouse.wms.model.Allocation;
import com.warehouse.wms.model.Inventory;
import com.warehouse.wms.model.OutboundLine;
import com.warehouse.wms.model.OutboundOrder;
import com.warehouse.wms.model.OutboundOrder.OrderStatus;
import com.warehouse.wms.repository.AllocationRepository;
import com.warehouse.wms.repository.InventoryRepository;
import com.warehouse.wms.repository.OutboundOrderRepository;
import com.warehouse.wms.service.InventoryLedgerService.LedgerRef;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class AllocationService {

    // The best row that still has stock: pick faces first, then reserve bins, then stock with no location; never
    // receiving, staging, pack, dock or inactive locations. One row at a time with SKIP LOCKED, so an allocation
    // only holds the rows it actually takes from and concurrent allocations move on to other rows.
    private static final String NEXT_ROW_SQL = """
            SELECT i.id
            FROM inventory i
                     LEFT JOIN locations l ON l.id = i.location_id
            WHERE i.warehouse_id = ? AND i.item_id = ? AND i.quantity > i.reserved_quantity
              AND (i.location_id IS NULL OR (l.is_active AND l.type IN ('PICK_FACE', 'RESERVE')))
            ORDER BY CASE l.type WHEN 'PICK_FACE' THEN 0 WHEN 'RESERVE' THEN 1 ELSE 2 END,
                     l.pick_sequence NULLS LAST, l.code, i.id
            LIMIT 1
            FOR UPDATE OF i SKIP LOCKED
            """;

    private final OutboundOrderRepository orderRepository;
    private final AllocationRepository allocationRepository;
    private final InventoryRepository inventoryRepository;
    private final InventoryLedgerService inventoryLedgerService;
    private final JdbcTemplate jdbcTemplate;

    /**
     * Allocates what each line still needs. The order becomes ALLOCATED when every line is covered and SHORT
     * otherwise; a SHORT order can be allocated again to fill the rest.
     */
    public OutboundOrder allocate(Long orderId) {
        log.info("Allocating order id: {}", orderId);
        OutboundOrder order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + orderId));
        if (!order.getStatus().canTransitionTo(OrderStatus.ALLOCATED)) {
            throw new InvalidStatusTransitionException("Cannot allocate an order in status " + order.getStatus());
        }

        LedgerRef ref = LedgerRef.of("ORDER", orderId);
        boolean complete = true;
        for (OutboundLine line : order.getLines()) {
            int missing = line.getQtyOrdered() - line.getQtyAllocated();
            if (missing > 0) {
                missing -= allocateLine(order, line, missing, ref).stream().mapToInt(Allocation::getQty).sum();
            }
            complete &= missing == 0;
        }

        order.setStatus(complete ? OrderStatus.ALLOCATED : OrderStatus.SHORT);
        log.info("Order {} is {}", orderId, order.getStatus());
        return order;
    }

    /** Releases every OPEN allocation of the order and marks it CANCELLED. */
    public void releaseOpenAllocations(OutboundOrder order) {
        LedgerRef ref = LedgerRef.of("ORDER", order.getId());
        allocationRepository.findByOrderId(order.getId()).stream()
                .filter(allocation -> allocation.getStatus() == Allocation.AllocationStatus.OPEN)
                // A consistent lock order keeps two cancellations from deadlocking
                .sorted(Comparator.comparing(Allocation::getInventoryId))
                .forEach(allocation -> {
                    inventoryLedgerService.deallocate(allocation.getInventoryId(), allocation.getQty(), ref);
                    allocation.setStatus(Allocation.AllocationStatus.CANCELLED);
                    OutboundLine line = allocation.getLine();
                    line.setQtyAllocated(line.getQtyAllocated() - allocation.getQty());
                });
    }

    /**
     * Allocates up to qty more for a line whose order is already past allocation (a short pick's remainder).
     * Returns the new allocations, which may cover less than qty.
     */
    public List<Allocation> reallocate(OutboundLine line, int qty) {
        OutboundOrder order = line.getOrder();
        log.info("Re-allocating {} for order {} line {}", qty, order.getId(), line.getLineNo());
        return allocateLine(order, line, qty, LedgerRef.of("ORDER", order.getId()));
    }

    private List<Allocation> allocateLine(OutboundOrder order, OutboundLine line, int missing, LedgerRef ref) {
        List<Allocation> allocations = new ArrayList<>();
        int allocated = 0;
        while (allocated < missing) {
            // The query is plain SQL, so this transaction's earlier allocations must be flushed for it to see them.
            // Every pass either completes the line or empties the row it took from, so the loop ends.
            inventoryRepository.flush();
            Optional<Long> next = jdbcTemplate.queryForList(NEXT_ROW_SQL, Long.class,
                    order.getWarehouse().getId(), line.getItem().getId()).stream().findFirst();
            if (next.isEmpty()) {
                break;
            }
            Long inventoryId = next.get();
            Inventory inventory = inventoryRepository.findByIdForUpdate(inventoryId).orElseThrow();
            int take = Math.min(missing - allocated, inventory.getQuantity() - inventory.getReservedQuantity());

            inventoryLedgerService.allocate(inventoryId, take, ref);
            Allocation allocation = new Allocation();
            allocation.setLine(line);
            allocation.setInventoryId(inventoryId);
            allocation.setQty(take);
            allocation.setStatus(Allocation.AllocationStatus.OPEN);
            allocations.add(allocationRepository.save(allocation));
            allocated += take;
        }

        line.setQtyAllocated(line.getQtyAllocated() + allocated);
        return allocations;
    }
}
