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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("itest")
class TaskApiTest {

    // Seeded zone Z-A1 in WH-001; its bins Z-A1-01 and Z-A1-02 are pick faces with pick_sequence 10 and 20
    private static final long ZONE_A1 = 1;

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
        sku = "SKU-TASK-" + UUID.randomUUID().toString().substring(0, 8);
        itemId = jdbcTemplate.queryForObject(
                "INSERT INTO items (sku, name, priority) VALUES (?, 'Task test item', 'LOW') RETURNING id", Long.class, sku);
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
    void releasedOrderIsPickedTaskByTaskIntoThePackLocation() throws Exception {
        stockInBins(Map.of("Z-A1-02", 3, "Z-A1-01", 5));
        String orderPath = allocatedOrder(7);

        HttpResponse<String> released = call("POST", orderPath + "/release", null, null, null);
        assertEquals(200, released.statusCode(), released.body());
        assertEquals("RELEASED", new JSONObject(released.body()).getString("status"));
        assertEquals(409, call("POST", orderPath + "/release", null, null, null).statusCode());
        assertEquals(List.of(Map.of("code", "Z-A1-01", "qty", 5, "sequence", 10, "status", "READY"),
                        Map.of("code", "Z-A1-02", "qty", 2, "sequence", 20, "status", "READY")),
                jdbcTemplate.queryForList("""
                        SELECT l.code, t.qty, t.sequence, t.status FROM tasks t JOIN locations l ON l.id = t.from_location_id
                        WHERE t.item_id = ? ORDER BY t.id""", itemId));

        // Pick sequence decides which task comes first
        JSONObject first = claim("picker-1");
        JSONAssert.assertEquals("""
                {"type": "PICK", "status": "CLAIMED", "qty": 5, "fromLocationCode": "Z-A1-01", "toLocationCode": "PACK-01",
                 "zoneId": 1, "priority": 5, "assignedUser": "picker-1", "refType": "ORDER"}
                """, first.toString(), JSONCompareMode.LENIENT);
        String confirmPath = "/api/v1/tasks/" + first.getLong("id") + "/confirm";

        assertMessage(call("POST", confirmPath, confirmJson("Z-A1-02", 5), "picker-1", null),
                400, "Scanned location Z-A1-02 does not match the task location Z-A1-01");
        assertMessage(call("POST", confirmPath, confirmJson("Z-A1-01", 6), "picker-1", null),
                400, "Quantity cannot exceed the task quantity of 5");
        assertEquals(409, call("POST", confirmPath, confirmJson("Z-A1-01", 5), "picker-2", null).statusCode());

        String key = "confirm-" + UUID.randomUUID();
        HttpResponse<String> confirmed = call("POST", confirmPath, confirmJson("Z-A1-01", 5), "picker-1", key);
        assertEquals(200, confirmed.statusCode(), confirmed.body());
        JSONAssert.assertEquals("""
                {"status": "DONE", "qtyConfirmed": 5}
                """, confirmed.body(), JSONCompareMode.LENIENT);
        assertEquals("PICKING", orderStatus(orderPath));

        // A retry with the same key changes nothing; without a key the task is simply no longer CLAIMED
        HttpResponse<String> repeated = call("POST", confirmPath, confirmJson("Z-A1-01", 5), "picker-1", key);
        assertEquals(200, repeated.statusCode());
        assertEquals("DONE", new JSONObject(repeated.body()).getString("status"));
        assertEquals(1, ledgerRows("PICK"));
        assertEquals(409, call("POST", confirmPath, confirmJson("Z-A1-01", 5), "picker-1", null).statusCode());

        JSONObject second = claim("picker-1");
        assertEquals("Z-A1-02", second.getString("fromLocationCode"));
        assertEquals(200, call("POST", "/api/v1/tasks/" + second.getLong("id") + "/confirm",
                confirmJson("Z-A1-02", 2), "picker-1", null).statusCode());

        JSONObject order = new JSONObject(call("GET", orderPath, null, null, null).body());
        JSONAssert.assertEquals("""
                {"status": "PICKED", "lines": [{"qtyAllocated": 7, "qtyPicked": 7}],
                 "allocations": [{"qty": 5, "status": "PICKED"}, {"qty": 2, "status": "PICKED"}]}
                """, order.toString(), JSONCompareMode.LENIENT);
        assertEquals(Map.of("quantity", 7, "reserved_quantity", 7), jdbcTemplate.queryForMap("""
                SELECT i.quantity, i.reserved_quantity FROM inventory i JOIN locations l ON l.id = i.location_id
                WHERE i.item_id = ? AND l.code = 'PACK-01'""", itemId));
        assertEquals(204, call("POST", "/api/v1/tasks/next?zoneId=" + ZONE_A1, null, "picker-1", null).statusCode());
        assertReconciles();
    }

    @Test
    void shortPickWritesOffTheMissingUnitsCreatesACountTaskAndReallocates() throws Exception {
        long shortBin = stockInBins(Map.of("Z-A1-01", 4, "Z-A1-02", 10)).get("Z-A1-01");
        String orderPath = allocatedOrder(6);
        call("POST", orderPath + "/release", null, null, null);

        JSONObject task = claim("picker-1");
        assertEquals("Z-A1-01", task.getString("fromLocationCode"));
        HttpResponse<String> shortPick = call("POST", "/api/v1/tasks/" + task.getLong("id") + "/confirm",
                confirmJson("Z-A1-01", 1), "picker-1", null);

        assertEquals(200, shortPick.statusCode(), shortPick.body());
        JSONAssert.assertEquals("""
                {"status": "SHORT", "qty": 4, "qtyConfirmed": 1}
                """, shortPick.body(), JSONCompareMode.LENIENT);
        // 1 picked and 3 written off leaves the bin empty
        assertEquals(Map.of("quantity", 0, "reserved_quantity", 0), jdbcTemplate.queryForMap(
                "SELECT quantity, reserved_quantity FROM inventory WHERE id = ?", shortBin));
        assertEquals(3, jdbcTemplate.queryForObject("SELECT qty FROM inventory_txn WHERE item_id = ? "
                + "AND txn_type = 'ADJUST_OUT' AND reason_code = 'SHORT_PICK'", Integer.class, itemId));
        assertEquals(Map.of("status", "READY", "ref_type", "TASK", "ref_id", task.getLong("id"), "from_location_id", binId("Z-A1-01")),
                jdbcTemplate.queryForMap("SELECT status, ref_type, ref_id, from_location_id FROM tasks "
                        + "WHERE item_id = ? AND type = 'COUNT'", itemId));

        JSONObject order = new JSONObject(call("GET", orderPath, null, null, null).body());
        JSONAssert.assertEquals("""
                {"status": "PICKING", "lines": [{"qtyOrdered": 6, "qtyAllocated": 6, "qtyPicked": 1}],
                 "allocations": [{"qty": 1, "status": "PICKED"}, {"qty": 2, "status": "OPEN"}, {"qty": 3, "status": "OPEN"}]}
                """, order.toString(), JSONCompareMode.LENIENT);

        // The original second task and the re-allocated remainder both come from Z-A1-02; the COUNT task is not handed out
        for (int qty : List.of(2, 3)) {
            JSONObject next = claim("picker-1");
            assertEquals(qty, next.getInt("qty"));
            assertEquals(200, call("POST", "/api/v1/tasks/" + next.getLong("id") + "/confirm",
                    confirmJson("Z-A1-02", qty), "picker-1", null).statusCode());
        }
        assertEquals(204, call("POST", "/api/v1/tasks/next?zoneId=" + ZONE_A1, null, "picker-1", null).statusCode());
        assertEquals("PICKED", orderStatus(orderPath));
        assertReconciles();
    }

    @Test
    void stockWithoutABinNeedsNoScanAndAnUnfillableShortStillFinishesTheOrder() throws Exception {
        HttpResponse<String> stock = call("POST", "/api/v1/inventory", """
                {"itemId": %d, "warehouseId": 1, "quantity": 2}
                """.formatted(itemId), null, null);
        assertEquals(201, stock.statusCode());
        String orderPath = allocatedOrder(2);
        call("POST", orderPath + "/release", null, null, null);

        long taskId = jdbcTemplate.queryForObject("SELECT id FROM tasks WHERE item_id = ? AND type = 'PICK'", Long.class, itemId);
        assertEquals(200, call("POST", "/api/v1/tasks/next", null, "picker-1", null).statusCode());
        HttpResponse<String> nothingFound = call("POST", "/api/v1/tasks/" + taskId + "/confirm", """
                {"qty": 0}
                """, "picker-1", null);

        assertEquals(200, nothingFound.statusCode(), nothingFound.body());
        assertEquals("SHORT", new JSONObject(nothingFound.body()).getString("status"));
        JSONAssert.assertEquals("""
                {"status": "PICKED", "lines": [{"qtyAllocated": 0, "qtyPicked": 0}], "allocations": [{"status": "CANCELLED"}]}
                """, call("GET", orderPath, null, null, null).body(), JSONCompareMode.LENIENT);
        assertReconciles();
    }

    @Test
    void cancellingAReleasedOrderCancelsItsTasks() throws Exception {
        stockInBins(Map.of("Z-A1-01", 2, "Z-A1-02", 2));
        String orderPath = allocatedOrder(4);
        call("POST", orderPath + "/release", null, null, null);
        JSONObject claimed = claim("picker-1");

        assertEquals(200, call("POST", orderPath + "/cancel", null, null, null).statusCode());

        assertEquals(List.of("CANCELLED", "CANCELLED"), jdbcTemplate.queryForList(
                "SELECT status FROM tasks WHERE item_id = ? ORDER BY id", String.class, itemId));
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT SUM(reserved_quantity) FROM inventory WHERE item_id = ?", Integer.class, itemId));
        assertEquals(409, call("POST", "/api/v1/tasks/" + claimed.getLong("id") + "/confirm",
                confirmJson("Z-A1-01", 2), "picker-1", null).statusCode());
        assertReconciles();
    }

    @Test
    void requestsWithoutAUserOrWithABadQuantityAreRejected() throws Exception {
        assertMessage(call("POST", "/api/v1/tasks/next", null, null, null), 400, "X-User-Id header is required");
        assertEquals(400, call("POST", "/api/v1/tasks/1/confirm", """
                {"qty": -1}
                """, "picker-1", null).statusCode());
        assertEquals(404, call("POST", "/api/v1/tasks/999999/confirm", confirmJson("X", 1), "picker-1", null).statusCode());
        assertEquals(404, call("GET", "/api/v1/tasks/999999", null, null, null).statusCode());
    }

    /** Receives the stock at RCV-01 and moves it into the given bins of WH-001; returns the bin rows' ids. */
    private Map<String, Long> stockInBins(Map<String, Integer> quantities) {
        int total = quantities.values().stream().mapToInt(Integer::intValue).sum();
        long receiving = ledger.receive(1L, itemId, binId("RCV-01"), total, LedgerRef.NONE).getId();
        Map<String, Long> rows = new HashMap<>();
        quantities.forEach((code, qty) -> rows.put(code, ledger.move(receiving, binId(code), qty, LedgerRef.NONE).getId()));
        return rows;
    }

    private String allocatedOrder(int qty) throws Exception {
        HttpResponse<String> created = call("POST", "/api/v1/orders", """
                {"externalOrderId": "OMS-%s", "warehouseCode": "WH-001", "priority": 5,
                 "lines": [{"lineNo": 1, "sku": "%s", "qty": %d}]}
                """.formatted(UUID.randomUUID(), sku, qty), null, null);
        assertEquals(201, created.statusCode(), created.body());
        String path = "/api/v1/orders/" + new JSONObject(created.body()).getLong("id");
        assertEquals(200, call("POST", path + "/allocate", null, null, null).statusCode());
        return path;
    }

    private JSONObject claim(String user) throws Exception {
        HttpResponse<String> response = call("POST", "/api/v1/tasks/next?zoneId=" + ZONE_A1, null, user, null);
        assertEquals(200, response.statusCode(), response.body());
        return new JSONObject(response.body());
    }

    private static String confirmJson(String locationCode, int qty) {
        return """
                {"locationCode": "%s", "qty": %d}
                """.formatted(locationCode, qty);
    }

    private String orderStatus(String orderPath) throws Exception {
        return new JSONObject(call("GET", orderPath, null, null, null).body()).getString("status");
    }

    private int ledgerRows(String type) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM inventory_txn WHERE item_id = ? AND txn_type = ?", Integer.class, itemId, type);
    }

    private long binId(String code) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM locations WHERE warehouse_id = 1 AND code = ?", Long.class, code);
    }

    private void assertReconciles() throws Exception {
        JSONArray mismatches = new JSONArray(call("GET", "/api/v1/inventory/reconciliation", null, null, null).body());
        for (int i = 0; i < mismatches.length(); i++) {
            assertTrue(mismatches.getJSONObject(i).getLong("itemId") != itemId, "ledger drift: " + mismatches);
        }
    }

    private void assertMessage(HttpResponse<String> response, int status, String message) throws Exception {
        assertEquals(status, response.statusCode(), response.body());
        assertEquals(message, new JSONObject(response.body()).getString("message"));
    }

    private HttpResponse<String> call(String method, String path, String jsonBody, String user, String idempotencyKey)
            throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
        if (user != null) {
            request.header("X-User-Id", user);
        }
        if (idempotencyKey != null) {
            request.header("Idempotency-Key", idempotencyKey);
        }
        if (jsonBody == null) {
            request.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            request.header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(jsonBody));
        }
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
