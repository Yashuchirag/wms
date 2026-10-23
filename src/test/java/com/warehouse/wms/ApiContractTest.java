package com.warehouse.wms;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.skyscreamer.jsonassert.Customization;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.skyscreamer.jsonassert.RegularExpressionValueMatcher;
import org.skyscreamer.jsonassert.comparator.CustomComparator;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pins the HTTP contract captured on Java 21 / Spring Boot 3.5.3 (see src/test/resources/api-baseline).
 * Uses the JDK HttpClient on purpose so this test needs no changes across the Spring Boot 4 upgrade.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("itest")
class ApiContractTest {

    private static final String ISO_DATE_TIME = "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?";

    private final HttpClient http = HttpClient.newHttpClient();

    @LocalServerPort
    private int port;

    @ParameterizedTest
    @ValueSource(strings = {"warehouses", "zones", "items", "inventory"})
    void listEndpointMatchesBaseline(String resource) throws Exception {
        HttpResponse<String> response = call("GET", "/api/v1/" + resource, null);

        assertEquals(200, response.statusCode());
        JSONAssert.assertEquals(baseline(resource), response.body(), JSONCompareMode.STRICT);
    }

    @Test
    void shipmentsMatchBaseline() throws Exception {
        HttpResponse<String> response = call("GET", "/api/v1/shipments", null);

        assertEquals(200, response.statusCode());
        // expectedDelivery is seeded relative to CURRENT_TIMESTAMP, so only its format is compared
        JSONAssert.assertEquals(baseline("shipments"), response.body(),
                strictExcept("[*].expectedDelivery", ISO_DATE_TIME + "|null"));
    }

    @Test
    void notFoundReturnsErrorResponse() throws Exception {
        HttpResponse<String> response = call("GET", "/api/v1/items/999", null);

        assertEquals(404, response.statusCode());
        JSONAssert.assertEquals("""
                {"timestamp": "", "status": 404, "error": "Not Found",
                 "message": "Item not found with id: 999", "path": "uri=/api/v1/items/999"}
                """, response.body(), strictExcept("timestamp", ISO_DATE_TIME));
    }

    @Test
    void invalidBodyReturnsFieldErrors() throws Exception {
        HttpResponse<String> response = call("POST", "/api/v1/items", "{}");

        assertEquals(400, response.statusCode());
        JSONAssert.assertEquals("""
                {"timestamp": "", "status": 400, "error": "Validation Failed",
                 "errors": {"sku": "SKU is required", "name": "Name is required", "priority": "Priority is required"}}
                """, response.body(), strictExcept("timestamp", ISO_DATE_TIME));
    }

    @Test
    void inventoryWritesArePersisted() throws Exception {
        HttpResponse<String> item = call("POST", "/api/v1/items",
                """
                {"sku": "SKU-CONTRACT", "name": "Contract test item", "priority": "LOW"}
                """);
        assertEquals(201, item.statusCode());
        long itemId = new JSONObject(item.body()).getLong("id");

        HttpResponse<String> created = call("POST", "/api/v1/inventory",
                """
                {"itemId": %d, "warehouseId": 3, "quantity": 40, "reorderPoint": 5}
                """.formatted(itemId));
        assertEquals(201, created.statusCode());
        JSONAssert.assertEquals("""
                {"itemId": %d, "warehouseId": 3, "zoneId": null, "quantity": 40, "reservedQuantity": 0,
                 "availableQuantity": 40, "reorderPoint": 5, "itemSku": "SKU-CONTRACT",
                 "itemName": "Contract test item", "warehouseCode": "WH-003", "zoneName": null}
                """.formatted(itemId), created.body(), JSONCompareMode.LENIENT);
        String inventoryPath = "/api/v1/inventory/" + new JSONObject(created.body()).getLong("id");

        // State is checked with a follow-up GET because adjust and reserve return a stale availableQuantity
        assertEquals(200, call("PATCH", inventoryPath + "/adjust?adjustment=-15&reason=contract", null).statusCode());
        assertQuantities(inventoryPath, 25, 0, 25);

        assertEquals(200, call("PATCH", inventoryPath + "/reserve?quantity=5", null).statusCode());
        assertQuantities(inventoryPath, 25, 5, 20);

        HttpResponse<String> overReserved = call("PATCH", inventoryPath + "/reserve?quantity=500", null);
        assertEquals(400, overReserved.statusCode());
        assertEquals("Insufficient available stock", new JSONObject(overReserved.body()).getString("message"));

        assertEquals("25", call("GET", "/api/v1/inventory/warehouse/3/total", null).body());

        assertEquals(204, call("DELETE", inventoryPath, null).statusCode());
        assertEquals(204, call("DELETE", "/api/v1/items/" + itemId, null).statusCode());
        assertEquals(404, call("GET", inventoryPath, null).statusCode());
    }

    private void assertQuantities(String inventoryPath, int quantity, int reserved, int available) throws Exception {
        HttpResponse<String> response = call("GET", inventoryPath, null);
        assertEquals(200, response.statusCode());
        JSONAssert.assertEquals("""
                {"quantity": %d, "reservedQuantity": %d, "availableQuantity": %d}
                """.formatted(quantity, reserved, available), response.body(), JSONCompareMode.LENIENT);
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

    private static String baseline(String resource) throws Exception {
        return new ClassPathResource("api-baseline/" + resource + ".json").getContentAsString(StandardCharsets.UTF_8);
    }

    private static CustomComparator strictExcept(String path, String regex) {
        return new CustomComparator(JSONCompareMode.STRICT,
                new Customization(path, new RegularExpressionValueMatcher<>(regex)));
    }
}
