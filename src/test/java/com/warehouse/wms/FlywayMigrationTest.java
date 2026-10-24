package com.warehouse.wms;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

        assertEquals(List.of("1", "2", "3", "4", "5", "6"), versions);
    }

    @Test
    @Transactional
    void updateTriggerRefreshesUpdatedAt() {
        jdbcTemplate.update("UPDATE warehouses SET name = name WHERE code = 'WH-003'");

        Boolean refreshed = jdbcTemplate.queryForObject(
                "SELECT updated_at > created_at FROM warehouses WHERE code = 'WH-003'", Boolean.class);

        assertTrue(refreshed);
    }
}
