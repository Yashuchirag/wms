package com.warehouse.wms;

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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("itest")
class IdempotencyTest {

    private final HttpClient http = HttpClient.newHttpClient();

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private long itemId;
    private String inventoryPath;

    @BeforeEach
    void createInventory() throws Exception {
        HttpResponse<String> item = call("POST", "/api/v1/items", null, """
                {"sku": "SKU-IDEMPOTENT", "name": "Idempotency test item", "priority": "LOW"}
                """);
        itemId = new JSONObject(item.body()).getLong("id");
        HttpResponse<String> inventory = call("POST", "/api/v1/inventory", null, """
                {"itemId": %d, "warehouseId": 3, "quantity": 30}
                """.formatted(itemId));
        inventoryPath = "/api/v1/inventory/" + new JSONObject(inventory.body()).getLong("id");
    }

    @AfterEach
    void deleteRows() {
        jdbcTemplate.update("DELETE FROM inventory_txn WHERE item_id = ?", itemId);
        jdbcTemplate.update("DELETE FROM inventory WHERE item_id = ?", itemId);
        jdbcTemplate.update("DELETE FROM items WHERE id = ?", itemId);
    }

    @Test
    void repeatedReserveWithTheSameKeyReservesOnce() throws Exception {
        String key = UUID.randomUUID().toString();

        HttpResponse<String> first = call("PATCH", inventoryPath + "/reserve?quantity=4", key, null);
        HttpResponse<String> second = call("PATCH", inventoryPath + "/reserve?quantity=4", key, null);

        assertEquals(200, first.statusCode());
        assertEquals(200, second.statusCode());
        assertQuantities(second.body(), 30, 4);
        assertQuantities(call("GET", inventoryPath, null, null).body(), 30, 4);
        assertEquals(1, ledgerRowsWithKey(key));
    }

    @Test
    void repeatedAdjustWithTheSameKeyAdjustsOnce() throws Exception {
        String key = UUID.randomUUID().toString();

        assertEquals(200, call("PATCH", inventoryPath + "/adjust?adjustment=-10&reason=damage", key, null).statusCode());
        assertEquals(200, call("PATCH", inventoryPath + "/adjust?adjustment=-10&reason=damage", key, null).statusCode());

        assertQuantities(call("GET", inventoryPath, null, null).body(), 20, 0);
        assertEquals(1, ledgerRowsWithKey(key));
    }

    @Test
    void differentKeysAndNoKeyAreIndependentRequests() throws Exception {
        call("PATCH", inventoryPath + "/reserve?quantity=1", UUID.randomUUID().toString(), null);
        call("PATCH", inventoryPath + "/reserve?quantity=1", UUID.randomUUID().toString(), null);
        call("PATCH", inventoryPath + "/reserve?quantity=1", null, null);
        call("PATCH", inventoryPath + "/reserve?quantity=1", null, null);

        assertQuantities(call("GET", inventoryPath, null, null).body(), 30, 4);
    }

    @Test
    void aRejectedRequestDoesNotUseUpItsKey() throws Exception {
        String key = UUID.randomUUID().toString();

        assertEquals(409, call("PATCH", inventoryPath + "/reserve?quantity=31", key, null).statusCode());
        assertEquals(200, call("PATCH", inventoryPath + "/reserve?quantity=3", key, null).statusCode());

        assertQuantities(call("GET", inventoryPath, null, null).body(), 30, 3);
    }

    private int ledgerRowsWithKey(String key) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM inventory_txn WHERE idempotency_key = ?", Integer.class, key);
    }

    private static void assertQuantities(String inventoryJson, int quantity, int reserved) throws Exception {
        JSONAssert.assertEquals("""
                {"quantity": %d, "reservedQuantity": %d, "availableQuantity": %d}
                """.formatted(quantity, reserved, quantity - reserved), inventoryJson, JSONCompareMode.LENIENT);
    }

    private HttpResponse<String> call(String method, String path, String idempotencyKey, String jsonBody) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
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
