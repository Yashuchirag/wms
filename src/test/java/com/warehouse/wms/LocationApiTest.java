package com.warehouse.wms;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
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

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("itest")
class LocationApiTest {

    private final HttpClient http = HttpClient.newHttpClient();

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void deleteTestLocations() {
        jdbcTemplate.update("DELETE FROM locations WHERE code LIKE 'TST-%'");
    }

    @Test
    void listFiltersByZoneAndType() throws Exception {
        // Seeded zone 1 is Z-A1 (pick faces), zone 3 is the bulk zone Z-C1 (reserve bins)
        assertEquals(List.of("Z-A1-01", "Z-A1-02", "Z-A1-03"), codes("/api/v1/locations?zoneId=1"));
        assertEquals(List.of("Z-C1-01", "Z-C1-02", "Z-C1-03"), codes("/api/v1/locations?zoneId=3&type=RESERVE"));
        assertEquals(List.of(), codes("/api/v1/locations?zoneId=1&type=RESERVE"));
        assertEquals(List.of("RCV-01"), codes("/api/v1/locations?type=RECEIVING"));
        assertEquals(17, new JSONArray(call("GET", "/api/v1/locations", null).body()).length());
    }

    @Test
    void locationRoundTrip() throws Exception {
        HttpResponse<String> created = call("POST", "/api/v1/locations", """
                {"code": "TST-01", "warehouseId": 3, "type": "STAGING"}
                """);
        assertEquals(201, created.statusCode());
        JSONAssert.assertEquals("""
                {"code": "TST-01", "warehouseId": 3, "zoneId": null, "type": "STAGING", "pickSequence": null,
                 "isActive": true, "isMixedSkuAllowed": false}
                """, created.body(), JSONCompareMode.LENIENT);
        String path = "/api/v1/locations/" + new JSONObject(created.body()).getLong("id");

        assertEquals(200, call("GET", path, null).statusCode());

        HttpResponse<String> updated = call("PUT", path, """
                {"code": "TST-01", "warehouseId": 3, "type": "STAGING", "pickSequence": 5, "isActive": false}
                """);
        assertEquals(200, updated.statusCode());
        JSONAssert.assertEquals("""
                {"pickSequence": 5, "isActive": false}
                """, updated.body(), JSONCompareMode.LENIENT);

        assertEquals(204, call("DELETE", path, null).statusCode());
        assertEquals(404, call("GET", path, null).statusCode());
    }

    @Test
    void codeIsUniquePerWarehouse() throws Exception {
        String body = """
                {"code": "TST-DUP", "warehouseId": %d, "type": "RESERVE"}
                """;
        assertEquals(201, call("POST", "/api/v1/locations", body.formatted(3)).statusCode());
        assertEquals(409, call("POST", "/api/v1/locations", body.formatted(3)).statusCode());
        assertEquals(201, call("POST", "/api/v1/locations", body.formatted(2)).statusCode());
    }

    @Test
    void zoneMustBelongToTheLocationsWarehouse() throws Exception {
        // Zone 1 is in warehouse 1
        HttpResponse<String> response = call("POST", "/api/v1/locations", """
                {"code": "TST-ZONE", "warehouseId": 3, "zoneId": 1, "type": "PICK_FACE"}
                """);

        assertEquals(400, response.statusCode());
        assertEquals("Zone Z-A1 belongs to another warehouse", new JSONObject(response.body()).getString("message"));
    }

    @Test
    void locationCannotMoveToAnotherWarehouse() throws Exception {
        HttpResponse<String> created = call("POST", "/api/v1/locations", """
                {"code": "TST-MOVE", "warehouseId": 3, "type": "DOCK"}
                """);
        String path = "/api/v1/locations/" + new JSONObject(created.body()).getLong("id");

        HttpResponse<String> moved = call("PUT", path, """
                {"code": "TST-MOVE", "warehouseId": 2, "type": "DOCK"}
                """);

        assertEquals(400, moved.statusCode());
    }

    @Test
    void invalidBodyReturnsFieldErrors() throws Exception {
        HttpResponse<String> response = call("POST", "/api/v1/locations", "{}");

        assertEquals(400, response.statusCode());
        JSONAssert.assertEquals("""
                {"status": 400, "error": "Validation Failed",
                 "errors": {"code": "Code is required", "warehouseId": "Warehouse ID is required", "type": "Type is required"}}
                """, response.body(), JSONCompareMode.LENIENT);
    }

    private List<String> codes(String path) throws Exception {
        HttpResponse<String> response = call("GET", path, null);
        assertEquals(200, response.statusCode());
        JSONArray locations = new JSONArray(response.body());
        List<String> codes = new ArrayList<>();
        for (int i = 0; i < locations.length(); i++) {
            codes.add(locations.getJSONObject(i).getString("code"));
        }
        return codes;
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
