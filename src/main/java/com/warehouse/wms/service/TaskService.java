package com.warehouse.wms.service;

import com.warehouse.wms.dto.TaskConfirmDTO;
import com.warehouse.wms.dto.TaskDTO;
import com.warehouse.wms.exception.InvalidStatusTransitionException;
import com.warehouse.wms.exception.ResourceNotFoundException;
import com.warehouse.wms.model.Allocation;
import com.warehouse.wms.model.Location;
import com.warehouse.wms.model.OutboundLine;
import com.warehouse.wms.model.OutboundOrder;
import com.warehouse.wms.model.OutboundOrder.OrderStatus;
import com.warehouse.wms.model.Task;
import com.warehouse.wms.model.Task.TaskStatus;
import com.warehouse.wms.model.Task.TaskType;
import com.warehouse.wms.repository.AllocationRepository;
import com.warehouse.wms.repository.LocationRepository;
import com.warehouse.wms.repository.OutboundOrderRepository;
import com.warehouse.wms.repository.TaskRepository;
import com.warehouse.wms.service.InventoryLedgerService.LedgerRef;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class TaskService {

    // SKIP LOCKED hands concurrent callers different tasks instead of making them wait for the same one.
    // Only PICK tasks are handed out for now: nothing can confirm a COUNT task yet.
    private static final String CLAIM_SQL = """
            UPDATE tasks SET status = 'CLAIMED', assigned_user = ?, claimed_at = CURRENT_TIMESTAMP
            WHERE id = (SELECT id
                        FROM tasks
                        WHERE status = 'READY' AND type = 'PICK' AND (CAST(? AS BIGINT) IS NULL OR zone_id = ?)
                        ORDER BY priority DESC, sequence NULLS LAST, id
                        LIMIT 1
                        FOR UPDATE SKIP LOCKED)
            RETURNING id
            """;

    // Where an allocation's stock sits: its bin (if any), that bin's zone or else the row's zone, and the pick order
    private static final String PICK_SOURCE_SQL = """
            SELECT i.location_id, COALESCE(l.zone_id, i.zone_id) AS zone_id, l.pick_sequence
            FROM inventory i
                     LEFT JOIN locations l ON l.id = i.location_id
            WHERE i.id = ?
            """;

    private final TaskRepository taskRepository;
    private final OutboundOrderRepository orderRepository;
    private final AllocationRepository allocationRepository;
    private final LocationRepository locationRepository;
    private final AllocationService allocationService;
    private final InventoryLedgerService inventoryLedgerService;
    private final JdbcTemplate jdbcTemplate;

    /** One PICK task per OPEN allocation of the order, each taking its stock to the warehouse's pack location. */
    public void createPickTasks(OutboundOrder order) {
        Long packLocationId = locationRepository.findPackLocation(order.getWarehouse().getId())
                .map(Location::getId)
                .orElseThrow(() -> new InvalidStatusTransitionException("Warehouse has no active PACK location"));
        allocationRepository.findByOrderId(order.getId()).stream()
                .filter(allocation -> allocation.getStatus() == Allocation.AllocationStatus.OPEN)
                .forEach(allocation -> createPickTask(order, allocation, packLocationId));
    }

    /** Cancels the order's tasks that nobody has finished. */
    public void cancelOpenTasks(Long orderId) {
        int cancelled = jdbcTemplate.update("UPDATE tasks SET status = 'CANCELLED' "
                + "WHERE ref_type = 'ORDER' AND ref_id = ? AND status IN ('READY', 'CLAIMED')", orderId);
        log.info("Cancelled {} tasks of order {}", cancelled, orderId);
    }

    /** Claims the best READY pick task, in one zone or in any; empty when there is none. */
    public Optional<TaskDTO> claimNext(Long zoneId, String user) {
        requireUser(user);
        Optional<Long> claimed = jdbcTemplate.queryForList(CLAIM_SQL, Long.class, user, zoneId, zoneId)
                .stream().findFirst();
        claimed.ifPresent(id -> log.info("Task {} claimed by {}", id, user));
        return claimed.map(id -> convertToDTO(taskRepository.findById(id).orElseThrow()));
    }

    @Transactional(readOnly = true)
    public TaskDTO getTask(Long id) {
        return convertToDTO(taskRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found with id: " + id)));
    }

    /**
     * Confirms a claimed pick. A quantity below the task's is a short pick: the missing units are written off,
     * the bin gets a COUNT task, and the remainder is allocated again with new pick tasks.
     */
    public TaskDTO confirm(Long taskId, TaskConfirmDTO request, String user, String idempotencyKey) {
        requireUser(user);
        // Read with JDBC so no unlocked Task enters the persistence context before the lock below
        Map<String, Object> ref = jdbcTemplate.queryForList(
                        "SELECT ref_type, ref_id FROM tasks WHERE id = ?", taskId).stream()
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Task not found with id: " + taskId));
        if (idempotencyKey != null && !inventoryLedgerService.claimIdempotencyKey(idempotencyKey)) {
            log.info("Idempotency-Key {} already processed; returning task {}", idempotencyKey, taskId);
            return getTask(taskId);
        }

        // Lock the order before the task, in the same order cancel uses, so the two cannot deadlock
        OutboundOrder order = "ORDER".equals(ref.get("ref_type"))
                ? orderRepository.findByIdForUpdate(((Number) ref.get("ref_id")).longValue()).orElseThrow()
                : null;
        Task task = taskRepository.findByIdForUpdate(taskId).orElseThrow();
        if (task.getType() != TaskType.PICK || order == null) {
            throw new InvalidStatusTransitionException("Only PICK tasks can be confirmed");
        }
        if (task.getStatus() != TaskStatus.CLAIMED) {
            throw new InvalidStatusTransitionException("Task " + taskId + " is " + task.getStatus() + ", not CLAIMED");
        }
        if (!user.equals(task.getAssignedUser())) {
            throw new InvalidStatusTransitionException("Task " + taskId + " is assigned to another user");
        }
        if (request.getQty() > task.getQty()) {
            throw new IllegalArgumentException("Quantity cannot exceed the task quantity of " + task.getQty());
        }
        checkScan(task, request.getLocationCode());

        int picked = request.getQty();
        int shortfall = task.getQty() - picked;
        Allocation allocation = allocationRepository.findById(task.getAllocationId()).orElseThrow();
        OutboundLine line = allocation.getLine();
        LedgerRef ledgerRef = new LedgerRef("ORDER", order.getId(), idempotencyKey);

        if (picked > 0) {
            inventoryLedgerService.pick(allocation.getInventoryId(), task.getToLocationId(), picked, ledgerRef);
            allocation.setQty(picked);
            allocation.setStatus(Allocation.AllocationStatus.PICKED);
        } else {
            allocation.setStatus(Allocation.AllocationStatus.CANCELLED);
        }
        line.setQtyPicked(line.getQtyPicked() + picked);
        task.setQtyConfirmed(picked);
        task.setStatus(shortfall > 0 ? TaskStatus.SHORT : TaskStatus.DONE);
        task.setCompletedAt(OffsetDateTime.now());
        moveOrderTo(order, OrderStatus.PICKING);

        if (shortfall > 0) {
            log.info("Short pick on task {}: {} of {}", taskId, picked, task.getQty());
            inventoryLedgerService.deallocate(allocation.getInventoryId(), shortfall, ledgerRef);
            inventoryLedgerService.adjust(allocation.getInventoryId(), -shortfall, "SHORT_PICK", ledgerRef);
            line.setQtyAllocated(line.getQtyAllocated() - shortfall);
            createCountTask(task);
            allocationService.reallocate(line, shortfall)
                    .forEach(fresh -> createPickTask(order, fresh, task.getToLocationId()));
        }

        // A short line with nothing left to pick does not hold the order back; qtyPicked shows the gap
        boolean openPicks = taskRepository.findByRef("ORDER", order.getId()).stream()
                .anyMatch(t -> t.getType() == TaskType.PICK
                        && (t.getStatus() == TaskStatus.READY || t.getStatus() == TaskStatus.CLAIMED));
        if (!openPicks) {
            moveOrderTo(order, OrderStatus.PICKED);
        }
        log.info("Task {} confirmed; order {} is {}", taskId, order.getId(), order.getStatus());
        return convertToDTO(task);
    }

    private void createPickTask(OutboundOrder order, Allocation allocation, Long packLocationId) {
        Map<String, Object> source = jdbcTemplate.queryForMap(PICK_SOURCE_SQL, allocation.getInventoryId());
        Task task = new Task();
        task.setWarehouseId(order.getWarehouse().getId());
        task.setType(TaskType.PICK);
        task.setStatus(TaskStatus.READY);
        task.setPriority(order.getPriority());
        task.setItemId(allocation.getLine().getItem().getId());
        task.setQty(allocation.getQty());
        task.setFromLocationId(toLong(source.get("location_id")));
        task.setToLocationId(packLocationId);
        task.setRefType("ORDER");
        task.setRefId(order.getId());
        task.setAllocationId(allocation.getId());
        task.setZoneId(toLong(source.get("zone_id")));
        task.setSequence(source.get("pick_sequence") == null ? null : ((Number) source.get("pick_sequence")).intValue());
        taskRepository.save(task);
    }

    private void createCountTask(Task shortTask) {
        Task count = new Task();
        count.setWarehouseId(shortTask.getWarehouseId());
        count.setType(TaskType.COUNT);
        count.setStatus(TaskStatus.READY);
        count.setPriority(shortTask.getPriority());
        count.setItemId(shortTask.getItemId());
        count.setFromLocationId(shortTask.getFromLocationId());
        count.setRefType("TASK");
        count.setRefId(shortTask.getId());
        count.setZoneId(shortTask.getZoneId());
        count.setSequence(shortTask.getSequence());
        taskRepository.save(count);
    }

    // Stock with no bin has nothing to scan, so only tasks with a from-location check the scan
    private void checkScan(Task task, String scannedCode) {
        if (task.getFromLocationId() == null) {
            return;
        }
        String expected = locationCode(task.getFromLocationId());
        if (!expected.equals(scannedCode)) {
            throw new IllegalArgumentException(
                    "Scanned location " + scannedCode + " does not match the task location " + expected);
        }
    }

    private void moveOrderTo(OutboundOrder order, OrderStatus next) {
        if (order.getStatus() != next && order.getStatus().canTransitionTo(next)) {
            order.setStatus(next);
        }
    }

    private static void requireUser(String user) {
        if (user == null || user.isBlank()) {
            throw new IllegalArgumentException("X-User-Id header is required");
        }
    }

    private static Long toLong(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }

    private String locationCode(Long locationId) {
        return locationId == null ? null : locationRepository.findById(locationId).map(Location::getCode).orElse(null);
    }

    private TaskDTO convertToDTO(Task task) {
        TaskDTO dto = new TaskDTO();
        dto.setId(task.getId());
        dto.setWarehouseId(task.getWarehouseId());
        dto.setType(task.getType());
        dto.setStatus(task.getStatus());
        dto.setPriority(task.getPriority());
        dto.setItemId(task.getItemId());
        dto.setQty(task.getQty());
        dto.setQtyConfirmed(task.getQtyConfirmed());
        dto.setFromLocationId(task.getFromLocationId());
        dto.setFromLocationCode(locationCode(task.getFromLocationId()));
        dto.setToLocationId(task.getToLocationId());
        dto.setToLocationCode(locationCode(task.getToLocationId()));
        dto.setRefType(task.getRefType());
        dto.setRefId(task.getRefId());
        dto.setAllocationId(task.getAllocationId());
        dto.setZoneId(task.getZoneId());
        dto.setSequence(task.getSequence());
        dto.setAssignedUser(task.getAssignedUser());
        dto.setClaimedAt(task.getClaimedAt());
        dto.setCompletedAt(task.getCompletedAt());
        dto.setCreatedAt(task.getCreatedAt());
        return dto;
    }
}
