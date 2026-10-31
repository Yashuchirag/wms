package com.warehouse.wms;

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

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("itest")
class ShipmentWorkflowTest {

    private final HttpClient http = HttpClient.newHttpClient();

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private long itemId;
    private long inventoryId;
    private long shipmentId;

    @BeforeEach
    void createRows() {
        itemId = jdbcTemplate.queryForObject(
                "INSERT INTO items (sku, name, priority) VALUES ('SKU-WORKFLOW', 'Workflow test item', 'LOW') RETURNING id",
                Long.class);
        inventoryId = jdbcTemplate.queryForObject(
                "INSERT INTO inventory (item_id, warehouse_id, quantity, reserved_quantity, available_quantity) "
                        + "VALUES (?, 3, 20, 0, 20) RETURNING id",
                Long.class, itemId);
        shipmentId = jdbcTemplate.queryForObject(
                "INSERT INTO shipments (tracking_number, warehouse_id, item_id, quantity, type, status) "
                        + "VALUES ('TRK-WORKFLOW', 3, ?, 5, 'OUTBOUND', 'PENDING') RETURNING id",
                Long.class, itemId);
    }

    @AfterEach
    void deleteRows() {
        jdbcTemplate.update("DELETE FROM shipments WHERE id = ?", shipmentId);
        jdbcTemplate.update("DELETE FROM inventory WHERE id = ?", inventoryId);
        jdbcTemplate.update("DELETE FROM items WHERE id = ?", itemId);
    }

    @Test
    void processingTwiceReservesOnce() throws Exception {
        HttpResponse<String> first = call("POST", "/api/v1/shipments/" + shipmentId + "/process");
        assertEquals(200, first.statusCode());
        assertEquals("PROCESSING", new JSONObject(first.body()).getString("status"));

        HttpResponse<String> second = call("POST", "/api/v1/shipments/" + shipmentId + "/process");
        assertEquals(409, second.statusCode());

        assertEquals(5, reservedQuantity());
    }

    @Test
    void illegalStatusTransitionReturnsConflict() throws Exception {
        assertEquals(409, updateStatus("DELIVERED").statusCode());
        assertEquals("PENDING", shipmentStatus());
    }

    @Test
    void processingCannotBeReachedThroughStatusUpdate() throws Exception {
        HttpResponse<String> response = updateStatus("PROCESSING");

        assertEquals(409, response.statusCode());
        assertEquals("PENDING", shipmentStatus());
        assertEquals(0, reservedQuantity());
    }

    @Test
    void allowedTransitionsSucceed() throws Exception {
        assertEquals(200, call("POST", "/api/v1/shipments/" + shipmentId + "/process").statusCode());
        assertEquals(200, updateStatus("SHIPPED").statusCode());
        assertEquals(200, updateStatus("DELIVERED").statusCode());
        assertEquals(409, updateStatus("CANCELLED").statusCode());
        assertEquals("DELIVERED", shipmentStatus());
    }

    private HttpResponse<String> updateStatus(String status) throws Exception {
        return call("PATCH", "/api/v1/shipments/" + shipmentId + "/status?status=" + status);
    }

    private int reservedQuantity() {
        return jdbcTemplate.queryForObject(
                "SELECT reserved_quantity FROM inventory WHERE id = ?", Integer.class, inventoryId);
    }

    private String shipmentStatus() {
        return jdbcTemplate.queryForObject("SELECT status FROM shipments WHERE id = ?", String.class, shipmentId);
    }

    private HttpResponse<String> call(String method, String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .method(method, HttpRequest.BodyPublishers.noBody()).build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
