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

        assertEquals(List.of("1", "2", "3", "4", "5", "6", "7"), versions);
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
}
