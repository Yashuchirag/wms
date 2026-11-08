package com.warehouse.wms;

import com.warehouse.wms.service.InventoryLedgerService;
import com.warehouse.wms.service.InventoryLedgerService.LedgerRef;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
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
class OrderApiTest {

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
        sku = "SKU-ORDER-" + UUID.randomUUID().toString().substring(0, 8);
        itemId = jdbcTemplate.queryForObject(
                "INSERT INTO items (sku, name, priority) VALUES (?, 'Order test item', 'LOW') RETURNING id", Long.class, sku);
    }

    @AfterEach
    void deleteRows() {
        // Orders first: their allocations block deleting inventory rows
        jdbcTemplate.update("DELETE FROM outbound_orders WHERE id IN (SELECT order_id FROM outbound_lines WHERE item_id = ?)", itemId);
        jdbcTemplate.update("DELETE FROM inventory_txn WHERE item_id = ?", itemId);
        jdbcTemplate.update("DELETE FROM inventory WHERE item_id = ?", itemId);
        jdbcTemplate.update("DELETE FROM items WHERE id = ?", itemId);
    }

    @Test
    void newOrderIsCreatedAndADuplicateReturnsTheExistingOrder() throws Exception {
        String body = orderJson("OMS-" + UUID.randomUUID(), 4);

        HttpResponse<String> first = call("POST", "/api/v1/orders", body);
        HttpResponse<String> second = call("POST", "/api/v1/orders", body);

        assertEquals(201, first.statusCode());
        assertEquals(200, second.statusCode());
        JSONAssert.assertEquals("""
                {"warehouseCode": "WH-001", "status": "CREATED", "priority": 5, "carrier": "UPS",
                 "shipTo": {"name": "Jane Smith", "address": {"city": "New York", "zip": "10001"}},
                 "lines": [{"lineNo": 1, "sku": "%s", "qtyOrdered": 4, "qtyAllocated": 0}], "allocations": []}
                """.formatted(sku), first.body(), JSONCompareMode.LENIENT);
        assertEquals(new JSONObject(first.body()).getLong("id"), new JSONObject(second.body()).getLong("id"));
        assertEquals(1, ordersForItem());
    }

    @Test
    void concurrentDuplicatesCreateOneOrder() throws Exception {
        String body = orderJson("OMS-" + UUID.randomUUID(), 1);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<HttpResponse<String>>> results = new ArrayList<>();

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < 10; i++) {
                results.add(executor.submit(() -> {
                    start.await();
                    return call("POST", "/api/v1/orders", body);
                }));
            }
            start.countDown();
        }

        List<Integer> statuses = new ArrayList<>();
        for (Future<HttpResponse<String>> result : results) {
            statuses.add(result.get().statusCode());
        }
        assertEquals(1, statuses.stream().filter(status -> status == 201).count(), statuses.toString());
        assertEquals(9, statuses.stream().filter(status -> status == 200).count(), statuses.toString());
        assertEquals(1, ordersForItem());
    }

    @Test
    void invalidOrdersAreRejected() throws Exception {
        HttpResponse<String> empty = call("POST", "/api/v1/orders", "{}");
        assertEquals(400, empty.statusCode());
        JSONAssert.assertEquals("""
                {"errors": {"externalOrderId": "External order ID is required",
                            "warehouseCode": "Warehouse code is required", "lines": "At least one line is required"}}
                """, empty.body(), JSONCompareMode.LENIENT);

        HttpResponse<String> badLine = call("POST", "/api/v1/orders", """
                {"externalOrderId": "OMS-BAD", "warehouseCode": "WH-001", "lines": [{"lineNo": 1, "sku": "%s", "qty": 0}]}
                """.formatted(sku));
        assertEquals(400, badLine.statusCode());
        assertEquals("Quantity must be at least 1", new JSONObject(badLine.body()).getJSONObject("errors").getString("lines[0].qty"));

        assertMessage(call("POST", "/api/v1/orders", """
                {"externalOrderId": "OMS-BAD", "warehouseCode": "WH-001", "lines": [{"lineNo": 1, "sku": "NO-SUCH-SKU", "qty": 1}]}
                """), 400, "Unknown SKU: NO-SUCH-SKU");
        assertMessage(call("POST", "/api/v1/orders", """
                {"externalOrderId": "OMS-BAD", "warehouseCode": "WH-999", "lines": [{"lineNo": 1, "sku": "%s", "qty": 1}]}
                """.formatted(sku)), 400, "Unknown warehouse code: WH-999");
        assertMessage(call("POST", "/api/v1/orders", """
                {"externalOrderId": "OMS-BAD", "warehouseCode": "WH-001",
                 "lines": [{"lineNo": 1, "sku": "%1$s", "qty": 1}, {"lineNo": 1, "sku": "%1$s", "qty": 2}]}
                """.formatted(sku)), 400, "Duplicate line number: 1");
        assertEquals(0, ordersForItem());
    }

    @Test
    void allocationReservesStockAndCancelReleasesIt() throws Exception {
        long inventoryId = stockWithoutLocation(10);
        String orderPath = createOrder(4);

        HttpResponse<String> allocated = call("POST", orderPath + "/allocate", null);
        assertEquals(200, allocated.statusCode());
        JSONAssert.assertEquals("""
                {"status": "ALLOCATED", "lines": [{"qtyAllocated": 4}],
                 "allocations": [{"lineNo": 1, "inventoryId": %d, "qty": 4, "status": "OPEN"}]}
                """.formatted(inventoryId), allocated.body(), JSONCompareMode.LENIENT);
        assertEquals(4, reserved(inventoryId));
        assertEquals(409, call("POST", orderPath + "/allocate", null).statusCode());

        HttpResponse<String> cancelled = call("POST", orderPath + "/cancel", null);
        assertEquals(200, cancelled.statusCode());
        JSONAssert.assertEquals("""
                {"status": "CANCELLED", "lines": [{"qtyAllocated": 0}], "allocations": [{"status": "CANCELLED"}]}
                """, cancelled.body(), JSONCompareMode.LENIENT);
        assertEquals(0, reserved(inventoryId));
        assertEquals(409, call("POST", orderPath + "/cancel", null).statusCode());
        assertEquals(409, call("POST", orderPath + "/allocate", null).statusCode());
        assertReconciles();
    }

    @Test
    void shortOrderKeepsWhatItGotAndCanBeAllocatedAgain() throws Exception {
        long inventoryId = stockWithoutLocation(3);
        String orderPath = createOrder(5);

        HttpResponse<String> shortResponse = call("POST", orderPath + "/allocate", null);
        JSONAssert.assertEquals("""
                {"status": "SHORT", "lines": [{"qtyOrdered": 5, "qtyAllocated": 3}]}
                """, shortResponse.body(), JSONCompareMode.LENIENT);

        assertEquals(200, call("PATCH", "/api/v1/inventory/" + inventoryId + "/adjust?adjustment=2&reason=found", null).statusCode());
        HttpResponse<String> filled = call("POST", orderPath + "/allocate", null);

        JSONAssert.assertEquals("""
                {"status": "ALLOCATED", "lines": [{"qtyAllocated": 5}],
                 "allocations": [{"qty": 3, "status": "OPEN"}, {"qty": 2, "status": "OPEN"}]}
                """, filled.body(), JSONCompareMode.LENIENT);
        assertEquals(5, reserved(inventoryId));
        assertReconciles();
    }

    @Test
    void allocationPrefersPickFacesThenReserveAndSkipsReceiving() throws Exception {
        long receivingRow = ledger.receive(1L, itemId, locationId("RCV-01"), 30, LedgerRef.NONE).getId();
        long reserveRow = ledger.move(receivingRow, locationId("Z-C1-01"), 10, LedgerRef.NONE).getId();
        long pickFaceRow = ledger.move(receivingRow, locationId("Z-A1-02"), 4, LedgerRef.NONE).getId();
        String orderPath = createOrder(9);

        HttpResponse<String> allocated = call("POST", orderPath + "/allocate", null);

        JSONAssert.assertEquals("""
                {"status": "ALLOCATED", "allocations": [{"inventoryId": %d, "qty": 4}, {"inventoryId": %d, "qty": 5}]}
                """.formatted(pickFaceRow, reserveRow), allocated.body(), JSONCompareMode.LENIENT);
        assertEquals(0, reserved(receivingRow));

        // Only the 16 units left at RCV-01 remain, and receiving stock is never allocated
        HttpResponse<String> second = call("POST", createOrder(10) + "/allocate", null);
        JSONAssert.assertEquals("""
                {"status": "SHORT", "lines": [{"qtyAllocated": 5}]}
                """, second.body(), JSONCompareMode.LENIENT);
        assertReconciles();
    }

    @Test
    void openAllocationsMatchReservedStock() throws Exception {
        stockWithoutLocation(20);
        for (int qty : List.of(3, 4, 5)) {
            assertEquals(200, call("POST", createOrder(qty) + "/allocate", null).statusCode());
        }
        call("POST", createOrder(2) + "/cancel", null);

        Integer mismatched = jdbcTemplate.queryForObject("""
                SELECT count(*) FROM inventory i
                WHERE i.item_id = ? AND i.reserved_quantity <> (SELECT COALESCE(SUM(a.qty), 0) FROM allocations a
                                                                WHERE a.inventory_id = i.id AND a.status = 'OPEN')
                """, Integer.class, itemId);
        assertEquals(0, mismatched);
        assertEquals(12, jdbcTemplate.queryForObject(
                "SELECT SUM(reserved_quantity) FROM inventory WHERE item_id = ?", Integer.class, itemId));
    }

    private long stockWithoutLocation(int quantity) throws Exception {
        HttpResponse<String> created = call("POST", "/api/v1/inventory", """
                {"itemId": %d, "warehouseId": 1, "quantity": %d}
                """.formatted(itemId, quantity));
        assertEquals(201, created.statusCode());
        return new JSONObject(created.body()).getLong("id");
    }

    private String createOrder(int qty) throws Exception {
        HttpResponse<String> created = call("POST", "/api/v1/orders", orderJson("OMS-" + UUID.randomUUID(), qty));
        assertEquals(201, created.statusCode());
        return "/api/v1/orders/" + new JSONObject(created.body()).getLong("id");
    }

    private String orderJson(String externalOrderId, int qty) {
        return """
                {"externalOrderId": "%s", "warehouseCode": "WH-001", "shipBy": "2026-11-20T17:00:00Z",
                 "carrier": "UPS", "serviceLevel": "GROUND", "priority": 5,
                 "shipTo": {"name": "Jane Smith", "address": {"line1": "1 Main St", "city": "New York", "zip": "10001"}},
                 "lines": [{"lineNo": 1, "sku": "%s", "qty": %d}]}
                """.formatted(externalOrderId, sku, qty);
    }

    private void assertReconciles() throws Exception {
        JSONArray mismatches = new JSONArray(call("GET", "/api/v1/inventory/reconciliation", null).body());
        for (int i = 0; i < mismatches.length(); i++) {
            assertTrue(mismatches.getJSONObject(i).getLong("itemId") != itemId, "ledger drift: " + mismatches);
        }
    }

    private void assertMessage(HttpResponse<String> response, int status, String message) throws Exception {
        assertEquals(status, response.statusCode(), response.body());
        assertEquals(message, new JSONObject(response.body()).getString("message"));
    }

    private int ordersForItem() {
        return jdbcTemplate.queryForObject(
                "SELECT count(DISTINCT order_id) FROM outbound_lines WHERE item_id = ?", Integer.class, itemId);
    }

    private int reserved(long inventoryId) {
        return jdbcTemplate.queryForObject(
                "SELECT reserved_quantity FROM inventory WHERE id = ?", Integer.class, inventoryId);
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
