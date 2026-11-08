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
import java.util.List;

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
        // By item, so ledger rows and any row created by receiving go too
        jdbcTemplate.update("DELETE FROM shipments WHERE item_id = ?", itemId);
        jdbcTemplate.update("DELETE FROM inventory_txn WHERE item_id = ?", itemId);
        jdbcTemplate.update("DELETE FROM inventory WHERE item_id = ?", itemId);
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

    @Test
    void shippingRemovesTheReservedStock() throws Exception {
        assertEquals(200, call("POST", "/api/v1/shipments/" + shipmentId + "/process").statusCode());
        assertEquals(200, updateStatus("SHIPPED").statusCode());

        assertEquals(15, quantity());
        assertEquals(0, reservedQuantity());
        assertEquals(List.of("ALLOCATE", "SHIP"), ledgerTypesForShipment(shipmentId));
    }

    @Test
    void cancellingAProcessingShipmentReleasesItsReservation() throws Exception {
        assertEquals(200, call("POST", "/api/v1/shipments/" + shipmentId + "/process").statusCode());
        assertEquals(5, reservedQuantity());

        assertEquals(200, updateStatus("CANCELLED").statusCode());

        assertEquals(20, quantity());
        assertEquals(0, reservedQuantity());
        assertEquals(List.of("ALLOCATE", "DEALLOCATE"), ledgerTypesForShipment(shipmentId));
    }

    @Test
    void inboundShipmentIsReceivedIntoTheReceivingLocation() throws Exception {
        long inboundId = jdbcTemplate.queryForObject(
                "INSERT INTO shipments (tracking_number, warehouse_id, item_id, quantity, type, status) "
                        + "VALUES ('TRK-WORKFLOW-IN', 3, ?, 7, 'INBOUND', 'PENDING') RETURNING id",
                Long.class, itemId);
        String inboundPath = "/api/v1/shipments/" + inboundId;

        assertEquals(200, call("POST", inboundPath + "/process").statusCode());
        assertEquals(0, reservedQuantity());
        assertEquals(409, call("PATCH", inboundPath + "/status?status=SHIPPED").statusCode());

        assertEquals(200, call("PATCH", inboundPath + "/status?status=RECEIVED").statusCode());

        Integer received = jdbcTemplate.queryForObject(
                "SELECT i.quantity FROM inventory i JOIN locations l ON l.id = i.location_id "
                        + "WHERE i.item_id = ? AND i.warehouse_id = 3 AND l.code = 'RCV-01'", Integer.class, itemId);
        assertEquals(7, received);
        assertEquals(20, quantity(), "the zone-level row is untouched");
        assertEquals(List.of("RECEIPT"), ledgerTypesForShipment(inboundId));
        assertEquals(409, call("PATCH", inboundPath + "/status?status=CANCELLED").statusCode());
    }

    @Test
    void outboundShipmentCannotBeReceived() throws Exception {
        assertEquals(200, call("POST", "/api/v1/shipments/" + shipmentId + "/process").statusCode());

        assertEquals(409, updateStatus("RECEIVED").statusCode());
        assertEquals("PROCESSING", shipmentStatus());
    }

    private HttpResponse<String> updateStatus(String status) throws Exception {
        return call("PATCH", "/api/v1/shipments/" + shipmentId + "/status?status=" + status);
    }

    private int quantity() {
        return jdbcTemplate.queryForObject("SELECT quantity FROM inventory WHERE id = ?", Integer.class, inventoryId);
    }

    private List<String> ledgerTypesForShipment(long id) {
        return jdbcTemplate.queryForList("SELECT txn_type FROM inventory_txn WHERE ref_type = 'SHIPMENT' AND ref_id = ? "
                + "ORDER BY id", String.class, id);
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
