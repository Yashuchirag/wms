package com.warehouse.wms;

import com.warehouse.wms.dto.ReconciliationMismatchDTO;
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
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("itest")
class AllocationConcurrencyTest {

    private static final int ORDERS = 20;
    private static final int QTY_PER_ORDER = 3;
    private static final int STOCK_PER_BIN = 10;

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
    void stockThreeBins() {
        sku = "SKU-ALLOC-" + UUID.randomUUID().toString().substring(0, 8);
        itemId = jdbcTemplate.queryForObject(
                "INSERT INTO items (sku, name, priority) VALUES (?, 'Allocation test item', 'LOW') RETURNING id",
                Long.class, sku);
        long receivingRow = ledger.receive(1L, itemId, locationId("RCV-01"), 3 * STOCK_PER_BIN, LedgerRef.NONE).getId();
        for (String bin : List.of("Z-A1-01", "Z-A1-02", "Z-A1-03")) {
            ledger.move(receivingRow, locationId(bin), STOCK_PER_BIN, LedgerRef.NONE);
        }
    }

    @AfterEach
    void deleteRows() {
        jdbcTemplate.update("DELETE FROM outbound_orders WHERE id IN (SELECT order_id FROM outbound_lines WHERE item_id = ?)", itemId);
        jdbcTemplate.update("DELETE FROM inventory_txn WHERE item_id = ?", itemId);
        jdbcTemplate.update("DELETE FROM inventory WHERE item_id = ?", itemId);
        jdbcTemplate.update("DELETE FROM items WHERE id = ?", itemId);
    }

    @Test
    void concurrentAllocationsNeverOverAllocate() throws Exception {
        List<Long> orderIds = new ArrayList<>();
        for (int i = 0; i < ORDERS; i++) {
            HttpResponse<String> created = call("POST", "/api/v1/orders", """
                    {"externalOrderId": "OMS-%s", "warehouseCode": "WH-001",
                     "lines": [{"lineNo": 1, "sku": "%s", "qty": %d}]}
                    """.formatted(UUID.randomUUID(), sku, QTY_PER_ORDER));
            orderIds.add(new JSONObject(created.body()).getLong("id"));
        }

        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (long orderId : orderIds) {
                results.add(executor.submit(() -> {
                    start.await();
                    return call("POST", "/api/v1/orders/" + orderId + "/allocate", null).statusCode();
                }));
            }
            start.countDown();
        }
        for (Future<Integer> result : results) {
            assertEquals(200, result.get());
        }

        // SKIP LOCKED means some orders go SHORT while stock is still free; never more than the stock is allocated
        int allocated = openAllocations();
        assertTrue(allocated > 0, "nothing was allocated");
        assertTrue(allocated <= 3 * STOCK_PER_BIN, "over-allocated: " + allocated);
        assertInvariants();

        // Retrying the SHORT orders one by one takes the rest, including the odd units left in partly reserved bins
        List<Long> shortOrders = jdbcTemplate.queryForList(
                "SELECT DISTINCT o.id FROM outbound_orders o JOIN outbound_lines l ON l.order_id = o.id "
                        + "WHERE l.item_id = ? AND o.status = 'SHORT' ORDER BY o.id", Long.class, itemId);
        for (long orderId : shortOrders) {
            assertEquals(200, call("POST", "/api/v1/orders/" + orderId + "/allocate", null).statusCode());
        }
        assertEquals(3 * STOCK_PER_BIN, openAllocations(), "demand exceeds stock, so all stock ends up allocated");
        assertInvariants();
    }

    private int openAllocations() {
        return jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(a.qty), 0) FROM allocations a JOIN outbound_lines l ON l.id = a.line_id "
                        + "WHERE l.item_id = ? AND a.status = 'OPEN'", Integer.class, itemId);
    }

    private void assertInvariants() {
        Integer rowsOutOfStep = jdbcTemplate.queryForObject("""
                SELECT count(*) FROM inventory i
                WHERE i.item_id = ? AND (i.reserved_quantity > i.quantity
                   OR i.reserved_quantity <> (SELECT COALESCE(SUM(a.qty), 0) FROM allocations a
                                              WHERE a.inventory_id = i.id AND a.status = 'OPEN'))
                """, Integer.class, itemId);
        assertEquals(0, rowsOutOfStep, "reserved stock must equal open allocations on every row");

        Integer ordersInWrongStatus = jdbcTemplate.queryForObject("""
                SELECT count(*) FROM outbound_orders o JOIN outbound_lines l ON l.order_id = o.id
                WHERE l.item_id = ? AND o.status <> CASE WHEN l.qty_allocated = l.qty_ordered THEN 'ALLOCATED' ELSE 'SHORT' END
                """, Integer.class, itemId);
        assertEquals(0, ordersInWrongStatus, "ALLOCATED exactly when the line is fully allocated");

        List<ReconciliationMismatchDTO> drift = ledger.findMismatches().stream()
                .filter(mismatch -> mismatch.itemId() == itemId).toList();
        assertEquals(List.of(), drift);
    }

    private long locationId(String code) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM locations WHERE warehouse_id = 1 AND code = ?", Long.class, code);
    }

    private HttpResponse<String> call(String method, String path, String jsonBody) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
        if (jsonBody == null) {
            request.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            request.header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(jsonBody));
        }
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
