package com.warehouse.wms;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("itest")
class FlywayMigrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void allMigrationsApplied() {
        List<String> versions = jdbcTemplate.queryForList(
                "SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank", String.class);

        assertEquals(List.of("1", "2", "3", "4", "5", "6", "7", "8", "9"), versions);
    }

    @Test
    @Transactional
    void updateTriggerRefreshesUpdatedAt() {
        jdbcTemplate.update("UPDATE warehouses SET name = name WHERE code = 'WH-003'");

        Boolean refreshed = jdbcTemplate.queryForObject(
                "SELECT updated_at > created_at FROM warehouses WHERE code = 'WH-003'", Boolean.class);

        assertTrue(refreshed);
    }

    @Test
    void reservedQuantityCannotExceedQuantity() {
        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update(
                "UPDATE inventory SET reserved_quantity = quantity + 1 WHERE id = 1"));
    }

    @Test
    void sameItemCannotBeStockedTwiceWithoutAZone() {
        // Seeded item 5 has no inventory in warehouse 3
        String insert = "INSERT INTO inventory (item_id, warehouse_id, zone_id, quantity) VALUES (5, 3, NULL, 1)";
        jdbcTemplate.update(insert);
        try {
            assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update(insert));
        } finally {
            jdbcTemplate.update("DELETE FROM inventory WHERE item_id = 5 AND warehouse_id = 3");
        }
    }

    @Test
    void locationsAreSeeded() {
        Integer bins = jdbcTemplate.queryForObject("SELECT count(*) FROM locations WHERE zone_id IS NOT NULL", Integer.class);
        List<String> mainWarehouseExtras = jdbcTemplate.queryForList(
                "SELECT l.code FROM locations l JOIN warehouses w ON w.id = l.warehouse_id "
                        + "WHERE w.code = 'WH-001' AND l.zone_id IS NULL ORDER BY l.code", String.class);

        assertEquals(15, bins);
        assertEquals(List.of("PACK-01", "RCV-01"), mainWarehouseExtras);
    }

    @Test
    void locationAndLpnValuesAreChecked() {
        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update(
                "INSERT INTO locations (warehouse_id, code, type) VALUES (3, 'BAD-01', 'SHELF')"));
        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update(
                "INSERT INTO lpns (warehouse_id, code, type, status) VALUES (3, 'LPN-BAD', 'PALLET', 'LOST')"));
    }

    @Test
    void zoneWithBinsCannotBeDeleted() {
        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update(
                "DELETE FROM zones WHERE code = 'Z-A1'"));
    }

    @Test
    @Transactional
    void deletingAWarehouseCascadesThroughLocationsAndLpns() {
        Long warehouseId = jdbcTemplate.queryForObject(
                "INSERT INTO warehouses (code, name) VALUES ('WH-CASCADE', 'Cascade test') RETURNING id", Long.class);
        Long zoneId = jdbcTemplate.queryForObject(
                "INSERT INTO zones (code, name, type, warehouse_id) VALUES ('Z-CASCADE', 'Cascade', 'BULK', ?) RETURNING id",
                Long.class, warehouseId);
        Long locationId = jdbcTemplate.queryForObject(
                "INSERT INTO locations (warehouse_id, zone_id, code, type) VALUES (?, ?, 'CAS-01', 'RESERVE') RETURNING id",
                Long.class, warehouseId, zoneId);
        Long palletId = jdbcTemplate.queryForObject(
                "INSERT INTO lpns (warehouse_id, code, type, location_id) VALUES (?, 'PLT-1', 'PALLET', ?) RETURNING id",
                Long.class, warehouseId, locationId);
        Long caseId = jdbcTemplate.queryForObject(
                "INSERT INTO lpns (warehouse_id, code, type, location_id, parent_lpn_id) VALUES (?, 'CASE-1', 'CASE', ?, ?) RETURNING id",
                Long.class, warehouseId, locationId, palletId);
        jdbcTemplate.update("INSERT INTO inventory (item_id, warehouse_id, zone_id, location_id, lpn_id, quantity) "
                + "VALUES (1, ?, ?, ?, ?, 5)", warehouseId, zoneId, locationId, caseId);

        jdbcTemplate.update("DELETE FROM warehouses WHERE id = ?", warehouseId);

        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT count(*) FROM locations WHERE warehouse_id = ?", Integer.class, warehouseId));
    }
}
