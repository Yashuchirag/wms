package com.warehouse.wms;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("itest")
class InventoryConcurrencyTest {

    private static final int THREADS = 50;
    private static final int STOCK = 30;

    private final HttpClient http = HttpClient.newHttpClient();

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private long itemId;
    private long inventoryId;

    @BeforeEach
    void createRow() {
        itemId = jdbcTemplate.queryForObject(
                "INSERT INTO items (sku, name, priority) VALUES ('SKU-CONCURRENCY', 'Concurrency test item', 'LOW') RETURNING id",
                Long.class);
        inventoryId = jdbcTemplate.queryForObject(
                "INSERT INTO inventory (item_id, warehouse_id, quantity, reserved_quantity, available_quantity) "
                        + "VALUES (?, 3, ?, 0, ?) RETURNING id",
                Long.class, itemId, STOCK, STOCK);
    }

    @AfterEach
    void deleteRow() {
        jdbcTemplate.update("DELETE FROM inventory WHERE id = ?", inventoryId);
        jdbcTemplate.update("DELETE FROM items WHERE id = ?", itemId);
    }

    @Test
    void concurrentReservationsNeverLoseUpdatesOrOversell() throws Exception {
        URI reserve = URI.create("http://localhost:" + port + "/api/v1/inventory/" + inventoryId + "/reserve?quantity=1");
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < THREADS; i++) {
                results.add(executor.submit(() -> {
                    start.await();
                    HttpRequest request = HttpRequest.newBuilder(reserve)
                            .method("PATCH", HttpRequest.BodyPublishers.noBody()).build();
                    return http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
                }));
            }
            start.countDown();
        }

        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> result : results) {
            statuses.add(result.get());
        }
        long succeeded = statuses.stream().filter(status -> status == 200).count();
        Integer reserved = jdbcTemplate.queryForObject(
                "SELECT reserved_quantity FROM inventory WHERE id = ?", Integer.class, inventoryId);

        assertEquals(succeeded, reserved.longValue(), "reserved_quantity must equal the successful reservations");
        assertTrue(reserved <= STOCK, "reserved_quantity must never exceed quantity");
        assertEquals(STOCK, succeeded, "every unit should be reservable exactly once");
        assertTrue(statuses.stream().allMatch(status -> status == 200 || status == 409),
                "rejections must be 409, got " + statuses);
    }
}
