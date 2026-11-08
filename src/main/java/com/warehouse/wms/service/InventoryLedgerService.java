package com.warehouse.wms.service;

import com.warehouse.wms.dto.ReconciliationMismatchDTO;
import com.warehouse.wms.exception.InsufficientStockException;
import com.warehouse.wms.exception.ResourceNotFoundException;
import com.warehouse.wms.model.Inventory;
import com.warehouse.wms.model.Location;
import com.warehouse.wms.repository.InventoryRepository;
import com.warehouse.wms.repository.LocationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * The only class that changes inventory quantities. Every method locks the balance rows it touches,
 * updates them and writes exactly one inventory_txn row, all in the caller's transaction.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class InventoryLedgerService {

    public enum TxnType {
        RECEIPT, MOVE, ALLOCATE, DEALLOCATE, PICK, SHIP, ADJUST_IN, ADJUST_OUT
    }

    /** What caused a ledger entry. Every field may be null. */
    public record LedgerRef(String type, Long id, String idempotencyKey) {
        public static final LedgerRef NONE = new LedgerRef(null, null, null);

        public static LedgerRef of(String type, Long id) {
            return new LedgerRef(type, id, null);
        }
    }

    private final InventoryRepository inventoryRepository;
    private final LocationRepository locationRepository;
    private final JdbcTemplate jdbcTemplate;

    @CacheEvict(value = "inventory", allEntries = true)
    public Inventory receive(Long warehouseId, Long itemId, Long toLocationId, int qty, LedgerRef ref) {
        requirePositive(qty);
        Location location = locationInWarehouse(toLocationId, warehouseId);
        Inventory inventory = lock(balanceRowId(warehouseId, itemId, location));

        inventory.setQuantity(inventory.getQuantity() + qty);
        record(TxnType.RECEIPT, inventory, qty, null, toLocationId, null, ref);
        return inventory;
    }

    @CacheEvict(value = "inventory", allEntries = true)
    public Inventory move(Long inventoryId, Long toLocationId, int qty, LedgerRef ref) {
        requirePositive(qty);
        Inventory[] rows = lockSourceAndTarget(inventoryId, toLocationId);
        Inventory source = rows[0];
        Inventory target = rows[1];
        if (available(source) < qty) {
            throw new InsufficientStockException("Insufficient available stock");
        }

        source.setQuantity(source.getQuantity() - qty);
        target.setQuantity(target.getQuantity() + qty);
        record(TxnType.MOVE, source, qty, source.getLocationId(), toLocationId, null, ref);
        return target;
    }

    @CacheEvict(value = "inventory", allEntries = true)
    public Inventory allocate(Long inventoryId, int qty, LedgerRef ref) {
        requirePositive(qty);
        Inventory inventory = lock(inventoryId);
        if (available(inventory) < qty) {
            throw new InsufficientStockException("Insufficient available stock");
        }

        inventory.setReservedQuantity(inventory.getReservedQuantity() + qty);
        record(TxnType.ALLOCATE, inventory, qty, inventory.getLocationId(), null, null, ref);
        return inventory;
    }

    @CacheEvict(value = "inventory", allEntries = true)
    public Inventory deallocate(Long inventoryId, int qty, LedgerRef ref) {
        requirePositive(qty);
        Inventory inventory = lock(inventoryId);
        if (inventory.getReservedQuantity() < qty) {
            throw new InsufficientStockException("Cannot release more than is reserved");
        }

        inventory.setReservedQuantity(inventory.getReservedQuantity() - qty);
        record(TxnType.DEALLOCATE, inventory, qty, inventory.getLocationId(), null, null, ref);
        return inventory;
    }

    /** Moves reserved stock to another location; the reservation moves with it. */
    @CacheEvict(value = "inventory", allEntries = true)
    public Inventory pick(Long inventoryId, Long toLocationId, int qty, LedgerRef ref) {
        requirePositive(qty);
        Inventory[] rows = lockSourceAndTarget(inventoryId, toLocationId);
        Inventory source = rows[0];
        Inventory target = rows[1];
        if (source.getReservedQuantity() < qty) {
            throw new InsufficientStockException("Insufficient reserved stock to pick");
        }

        source.setQuantity(source.getQuantity() - qty);
        source.setReservedQuantity(source.getReservedQuantity() - qty);
        target.setQuantity(target.getQuantity() + qty);
        target.setReservedQuantity(target.getReservedQuantity() + qty);
        record(TxnType.PICK, source, qty, source.getLocationId(), toLocationId, null, ref);
        return target;
    }

    /** Removes reserved stock from the warehouse. */
    @CacheEvict(value = "inventory", allEntries = true)
    public Inventory ship(Long inventoryId, int qty, LedgerRef ref) {
        requirePositive(qty);
        Inventory inventory = lock(inventoryId);
        if (inventory.getReservedQuantity() < qty) {
            throw new InsufficientStockException("Insufficient reserved stock to ship");
        }

        inventory.setQuantity(inventory.getQuantity() - qty);
        inventory.setReservedQuantity(inventory.getReservedQuantity() - qty);
        record(TxnType.SHIP, inventory, qty, inventory.getLocationId(), null, null, ref);
        return inventory;
    }

    /** Changes quantity by a signed delta. A zero delta changes nothing and writes no ledger row. */
    @CacheEvict(value = "inventory", allEntries = true)
    public Inventory adjust(Long inventoryId, int delta, String reasonCode, LedgerRef ref) {
        Inventory inventory = lock(inventoryId);
        if (delta == 0) {
            return inventory;
        }
        if (inventory.getQuantity() + delta < inventory.getReservedQuantity()) {
            throw new InsufficientStockException("Insufficient stock");
        }

        inventory.setQuantity(inventory.getQuantity() + delta);
        if (delta > 0) {
            record(TxnType.ADJUST_IN, inventory, delta, null, inventory.getLocationId(), reasonCode, ref);
        } else {
            record(TxnType.ADJUST_OUT, inventory, -delta, inventory.getLocationId(), null, reasonCode, ref);
        }
        return inventory;
    }

    /**
     * Records the key in the caller's transaction. Returns false if it was already used, in which case the
     * caller must change nothing. A concurrent request with the same key waits here until the first commits.
     */
    public boolean claimIdempotencyKey(String key) {
        return jdbcTemplate.update(
                "INSERT INTO processed_requests (idempotency_key) VALUES (?) ON CONFLICT DO NOTHING", key) == 1;
    }

    /** Groups whose net ledger quantity or reservation differs from the inventory rows. Empty means balanced. */
    @Transactional(readOnly = true)
    public List<ReconciliationMismatchDTO> findMismatches() {
        return jdbcTemplate.query(RECONCILIATION_SQL, (rs, rowNum) -> new ReconciliationMismatchDTO(
                rs.getLong("warehouse_id"),
                rs.getLong("item_id"),
                rs.getLong("location_id") == 0 ? null : rs.getLong("location_id"),
                rs.getLong("ledger_quantity"),
                rs.getLong("balance_quantity"),
                rs.getLong("ledger_reserved"),
                rs.getLong("balance_reserved")));
    }

    // Location 0 stands for "no location": FULL JOIN needs a plain equality, which IS NOT DISTINCT FROM is not
    private static final String RECONCILIATION_SQL = """
            WITH effects AS (
                SELECT warehouse_id, item_id, COALESCE(from_location_id, 0) AS location_id,
                       CASE WHEN txn_type IN ('MOVE', 'PICK', 'SHIP', 'ADJUST_OUT') THEN -qty ELSE 0 END AS dq,
                       CASE WHEN txn_type = 'ALLOCATE' THEN qty
                            WHEN txn_type IN ('DEALLOCATE', 'PICK', 'SHIP') THEN -qty ELSE 0 END AS dr
                FROM inventory_txn
                WHERE txn_type IN ('MOVE', 'PICK', 'SHIP', 'ADJUST_OUT', 'ALLOCATE', 'DEALLOCATE')
                UNION ALL
                SELECT warehouse_id, item_id, COALESCE(to_location_id, 0),
                       qty,
                       CASE WHEN txn_type = 'PICK' THEN qty ELSE 0 END
                FROM inventory_txn
                WHERE txn_type IN ('RECEIPT', 'ADJUST_IN', 'MOVE', 'PICK')
            ),
            ledger AS (
                SELECT warehouse_id, item_id, location_id, SUM(dq) AS qty, SUM(dr) AS reserved
                FROM effects GROUP BY warehouse_id, item_id, location_id
            ),
            balance AS (
                SELECT warehouse_id, item_id, COALESCE(location_id, 0) AS location_id,
                       SUM(quantity) AS qty, SUM(reserved_quantity) AS reserved
                FROM inventory GROUP BY warehouse_id, item_id, COALESCE(location_id, 0)
            )
            SELECT COALESCE(l.warehouse_id, b.warehouse_id) AS warehouse_id,
                   COALESCE(l.item_id, b.item_id) AS item_id,
                   COALESCE(l.location_id, b.location_id) AS location_id,
                   COALESCE(l.qty, 0) AS ledger_quantity, COALESCE(b.qty, 0) AS balance_quantity,
                   COALESCE(l.reserved, 0) AS ledger_reserved, COALESCE(b.reserved, 0) AS balance_reserved
            FROM ledger l
                     FULL JOIN balance b
                               ON l.warehouse_id = b.warehouse_id AND l.item_id = b.item_id AND l.location_id = b.location_id
            WHERE COALESCE(l.qty, 0) <> COALESCE(b.qty, 0) OR COALESCE(l.reserved, 0) <> COALESCE(b.reserved, 0)
            ORDER BY 1, 2, 3
            """;

    /** Locks the source row and the target row at toLocationId (created if missing), lower id first. */
    private Inventory[] lockSourceAndTarget(Long inventoryId, Long toLocationId) {
        // Read with JDBC so no unlocked Inventory entity enters the persistence context; warehouse and item never change
        Map<String, Object> source = jdbcTemplate.queryForList(
                        "SELECT warehouse_id, item_id FROM inventory WHERE id = ?", inventoryId).stream()
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Inventory not found"));
        Long warehouseId = ((Number) source.get("warehouse_id")).longValue();
        Long itemId = ((Number) source.get("item_id")).longValue();

        Long targetId = balanceRowId(warehouseId, itemId, locationInWarehouse(toLocationId, warehouseId));
        if (targetId.equals(inventoryId)) {
            throw new IllegalArgumentException("Stock is already at location " + toLocationId);
        }

        // A consistent lock order prevents deadlocks between opposite moves
        if (inventoryId < targetId) {
            Inventory sourceRow = lock(inventoryId);
            return new Inventory[]{sourceRow, lock(targetId)};
        }
        Inventory targetRow = lock(targetId);
        return new Inventory[]{lock(inventoryId), targetRow};
    }

    /** Id of the balance row for this item at this location, inserting an empty row if there is none. */
    private Long balanceRowId(Long warehouseId, Long itemId, Location location) {
        Long zoneId = location.getZone() != null ? location.getZone().getId() : null;
        jdbcTemplate.update("INSERT INTO inventory (item_id, warehouse_id, zone_id, location_id) "
                        + "VALUES (?, ?, CAST(? AS BIGINT), ?) ON CONFLICT ON CONSTRAINT uq_inventory_balance DO NOTHING",
                itemId, warehouseId, zoneId, location.getId());
        return jdbcTemplate.queryForObject("SELECT id FROM inventory WHERE item_id = ? AND warehouse_id = ? "
                        + "AND zone_id IS NOT DISTINCT FROM CAST(? AS BIGINT) AND location_id = ?",
                Long.class, itemId, warehouseId, zoneId, location.getId());
    }

    private Location locationInWarehouse(Long locationId, Long warehouseId) {
        Location location = locationRepository.findById(locationId)
                .orElseThrow(() -> new ResourceNotFoundException("Location not found"));
        if (!location.getWarehouse().getId().equals(warehouseId)) {
            throw new IllegalArgumentException("Location " + location.getCode() + " belongs to another warehouse");
        }
        return location;
    }

    private Inventory lock(Long inventoryId) {
        return inventoryRepository.findByIdForUpdate(inventoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory not found"));
    }

    // Computed, because the stored availableQuantity is only refreshed at flush
    private static int available(Inventory inventory) {
        return inventory.getQuantity() - inventory.getReservedQuantity();
    }

    private static void requirePositive(int qty) {
        if (qty <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
    }

    private void record(TxnType type, Inventory inventory, int qty, Long fromLocationId, Long toLocationId,
                        String reasonCode, LedgerRef ref) {
        jdbcTemplate.update("INSERT INTO inventory_txn (warehouse_id, txn_type, item_id, qty, from_location_id, "
                        + "to_location_id, ref_type, ref_id, reason_code, idempotency_key) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                inventory.getWarehouse().getId(), type.name(), inventory.getItem().getId(), qty,
                fromLocationId, toLocationId, ref.type(), ref.id(), reasonCode, ref.idempotencyKey());
        log.info("Ledger {} of {} for item {} in warehouse {}", type, qty,
                inventory.getItem().getId(), inventory.getWarehouse().getId());
    }
}
