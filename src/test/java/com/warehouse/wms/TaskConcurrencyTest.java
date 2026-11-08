package com.warehouse.wms;

import com.warehouse.wms.service.InventoryLedgerService;
import com.warehouse.wms.service.InventoryLedgerService.LedgerRef;
import org.json.JSONObject;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("itest")
class TaskConcurrencyTest {

    private static final int TASKS = 20;

    private final HttpClient http = HttpClient.newHttpClient();

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private InventoryLedgerService ledger;

    private long itemId;
    private String sku;

    @BeforeEach
    void createItem() {
        sku = "SKU-CLAIM-" + UUID.randomUUID().toString().substring(0, 8);
        itemId = jdbcTemplate.queryForObject(
                "INSERT INTO items (sku, name, priority) VALUES (?, 'Claim test item', 'LOW') RETURNING id", Long.class, sku);
    }

    @AfterEach
    void deleteRows() {
        jdbcTemplate.update("DELETE FROM tasks WHERE item_id = ?", itemId);
        jdbcTemplate.update("DELETE FROM outbound_orders WHERE id IN (SELECT order_id FROM outbound_lines WHERE item_id = ?)", itemId);
        jdbcTemplate.update("DELETE FROM inventory_txn WHERE item_id = ?", itemId);
        jdbcTemplate.update("DELETE FROM inventory WHERE item_id = ?", itemId);
        jdbcTemplate.update("DELETE FROM items WHERE id = ?", itemId);
    }

    @Test
    void concurrentClaimsGetDifferentTasks() throws Exception {
        long bin = jdbcTemplate.queryForObject("SELECT id FROM locations WHERE warehouse_id = 1 AND code = 'Z-B1-01'", Long.class);
        long zoneId = jdbcTemplate.queryForObject("SELECT zone_id FROM locations WHERE id = ?", Long.class, bin);
        long receiving = ledger.receive(1L, itemId, rcv(), TASKS, LedgerRef.NONE).getId();
        ledger.move(receiving, bin, TASKS, LedgerRef.NONE);

        // One order with twenty one-unit lines gives twenty pick tasks
        String lines = IntStream.rangeClosed(1, TASKS)
                .mapToObj(n -> "{\"lineNo\": %d, \"sku\": \"%s\", \"qty\": 1}".formatted(n, sku))
                .collect(Collectors.joining(", "));
        HttpResponse<String> created = call("/api/v1/orders", """
                {"externalOrderId": "OMS-%s", "warehouseCode": "WH-001", "lines": [%s]}
                """.formatted(UUID.randomUUID(), lines), null);
        String orderPath = "/api/v1/orders/" + new JSONObject(created.body()).getLong("id");
        assertEquals(200, call(orderPath + "/allocate", null, null).statusCode());
        assertEquals(200, call(orderPath + "/release", null, null).statusCode());

        CountDownLatch start = new CountDownLatch(1);
        List<Future<HttpResponse<String>>> results = new ArrayList<>();
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < TASKS; i++) {
                String user = "picker-" + i;
                results.add(executor.submit(() -> {
                    start.await();
                    return call("/api/v1/tasks/next?zoneId=" + zoneId, null, user);
                }));
            }
            start.countDown();
        }

        Set<Long> taskIds = new HashSet<>();
        for (Future<HttpResponse<String>> result : results) {
            HttpResponse<String> response = result.get();
            assertEquals(200, response.statusCode(), response.body());
            taskIds.add(new JSONObject(response.body()).getLong("id"));
        }
        assertEquals(TASKS, taskIds.size(), "every caller must get a different task");
        assertEquals(TASKS, jdbcTemplate.queryForObject("SELECT count(DISTINCT assigned_user) FROM tasks "
                + "WHERE item_id = ? AND status = 'CLAIMED'", Integer.class, itemId));
        assertEquals(204, call("/api/v1/tasks/next?zoneId=" + zoneId, null, "picker-late").statusCode());
    }

    private long rcv() {
        return jdbcTemplate.queryForObject("SELECT id FROM locations WHERE warehouse_id = 1 AND code = 'RCV-01'", Long.class);
    }

    private HttpResponse<String> call(String path, String jsonBody, String user) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
        if (user != null) {
            request.header("X-User-Id", user);
        }
        if (jsonBody == null) {
            request.POST(HttpRequest.BodyPublishers.noBody());
        } else {
            request.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(jsonBody));
        }
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
