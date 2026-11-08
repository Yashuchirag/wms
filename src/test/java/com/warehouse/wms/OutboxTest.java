package com.warehouse.wms;

import com.warehouse.wms.outbox.LoggingEventPublisher;
import com.warehouse.wms.outbox.OutboxRelay;
import com.warehouse.wms.outbox.OutboxWriter;
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
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("itest")
class OutboxTest {

    // Aggregate ids far above anything the tests create, so test events never mix with real orders' events
    private static final long TEST_AGGREGATE = 900_000_000L;

    private final HttpClient http = HttpClient.newHttpClient();

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private OutboxWriter outboxWriter;

    @Autowired
    private OutboxRelay relay;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private long itemId;
    private String sku;

    @BeforeEach
    void createItemAndDrainOutbox() {
        sku = "SKU-OUTBOX-" + UUID.randomUUID().toString().substring(0, 8);
        itemId = jdbcTemplate.queryForObject(
                "INSERT INTO items (sku, name, priority) VALUES (?, 'Outbox test item', 'LOW') RETURNING id", Long.class, sku);
        // Other test classes leave unsent events behind (the scheduler is off in tests)
        while (relay.publishBatch() > 0) {
        }
    }

    @AfterEach
    void deleteRows() {
        jdbcTemplate.update("DELETE FROM outbox_events WHERE aggregate_type = 'ORDER' AND aggregate_id IN "
                + "(SELECT order_id FROM outbound_lines WHERE item_id = ?)", itemId);
        jdbcTemplate.update("DELETE FROM outbox_events WHERE aggregate_id >= ?", TEST_AGGREGATE);
        jdbcTemplate.update("DELETE FROM outbound_orders WHERE id IN (SELECT order_id FROM outbound_lines WHERE item_id = ?)", itemId);
        jdbcTemplate.update("DELETE FROM inventory_txn WHERE item_id = ?", itemId);
        jdbcTemplate.update("DELETE FROM inventory WHERE item_id = ?", itemId);
        jdbcTemplate.update("DELETE FROM items WHERE id = ?", itemId);
    }

    @Test
    void orderIntakeAndAllocationWriteEvents() throws Exception {
        assertEquals(201, call("POST", "/api/v1/inventory", """
                {"itemId": %d, "warehouseId": 1, "quantity": 3}
                """.formatted(itemId)).statusCode());
        String body = """
                {"externalOrderId": "OMS-%s", "warehouseCode": "WH-001", "lines": [{"lineNo": 1, "sku": "%s", "qty": 5}]}
                """.formatted(UUID.randomUUID(), sku);
        HttpResponse<String> created = call("POST", "/api/v1/orders", body);
        long orderId = new JSONObject(created.body()).getLong("id");
        assertEquals(200, call("POST", "/api/v1/orders", body).statusCode());

        List<Map<String, Object>> events = events(orderId);
        assertEquals(List.of("OrderAccepted.v1"), events.stream().map(e -> e.get("event_type")).toList(),
                "a duplicate order must not write a second event");
        JSONAssert.assertEquals("""
                {"eventId": %d, "eventType": "OrderAccepted.v1", "orderId": %d, "warehouseCode": "WH-001", "status": "CREATED",
                 "lines": [{"lineNo": 1, "sku": "%s", "qtyOrdered": 5, "qtyAllocated": 0}]}
                """.formatted((Long) events.get(0).get("id"), orderId, sku),
                (String) events.get(0).get("payload"), JSONCompareMode.LENIENT);
        assertNotNull(new JSONObject((String) events.get(0).get("payload")).getString("occurredAt"));

        // 3 of 5 is SHORT; after 2 more arrive the retry is ALLOCATED
        call("POST", "/api/v1/orders/" + orderId + "/allocate", null);
        long inventoryId = jdbcTemplate.queryForObject("SELECT id FROM inventory WHERE item_id = ?", Long.class, itemId);
        call("PATCH", "/api/v1/inventory/" + inventoryId + "/adjust?adjustment=2&reason=found", null);
        call("POST", "/api/v1/orders/" + orderId + "/allocate", null);

        events = events(orderId);
        assertEquals(List.of("OrderAccepted.v1", "OrderShort.v1", "OrderAllocated.v1"),
                events.stream().map(e -> e.get("event_type")).toList());
        JSONAssert.assertEquals("""
                {"status": "SHORT", "lines": [{"qtyAllocated": 3}]}
                """, (String) events.get(1).get("payload"), JSONCompareMode.LENIENT);
    }

    @Test
    void anEventExistsOnlyIfItsTransactionCommits() {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        assertThrows(IllegalStateException.class, () -> transaction.executeWithoutResult(status -> {
            outboxWriter.write("TEST", TEST_AGGREGATE, "Test.v1", Map.of("n", 1));
            throw new IllegalStateException("business change failed");
        }));
        assertEquals(0, countEvents(TEST_AGGREGATE));

        transaction.executeWithoutResult(status -> outboxWriter.write("TEST", TEST_AGGREGATE, "Test.v1", Map.of("n", 2)));
        assertEquals(1, countEvents(TEST_AGGREGATE));

        // Outside a transaction the writer refuses, rather than writing an event no change backs
        assertThrows(IllegalTransactionStateException.class,
                () -> outboxWriter.write("TEST", TEST_AGGREGATE, "Test.v1", Map.of("n", 3)));
    }

    @Test
    void relayPublishesInIdOrderAndMarksRowsPublished() {
        List<Long> ids = writeEvents(TEST_AGGREGATE, TEST_AGGREGATE, TEST_AGGREGATE + 1);
        List<Long> seen = new ArrayList<>();
        OutboxRelay recording = new OutboxRelay(jdbcTemplate, event -> seen.add(event.id()), transactionManager);

        assertEquals(3, recording.publishBatch());

        assertEquals(ids, seen);
        assertEquals(0, jdbcTemplate.queryForObject("SELECT count(*) FROM outbox_events WHERE aggregate_id >= ? "
                + "AND (published_at IS NULL OR attempts <> 1)", Integer.class, TEST_AGGREGATE));
        assertEquals(0, recording.publishBatch());
    }

    @Test
    void aFailedEventIsKeptAndHoldsBackOnlyItsOwnAggregate() {
        long failing = TEST_AGGREGATE;
        long healthy = TEST_AGGREGATE + 1;
        List<Long> ids = writeEvents(failing, healthy, failing);
        OutboxRelay flaky = new OutboxRelay(jdbcTemplate, event -> {
            if (event.aggregateId() == failing) {
                throw new IllegalStateException("broker unavailable");
            }
        }, transactionManager);

        assertEquals(1, flaky.publishBatch());
        assertEquals(0, flaky.publishBatch());

        Map<String, Object> first = row(ids.get(0));
        assertNull(first.get("published_at"));
        assertEquals(2, first.get("attempts"));
        assertTrue(((String) first.get("last_error")).contains("broker unavailable"), (String) first.get("last_error"));
        assertNotNull(row(ids.get(1)).get("published_at"));
        // The later event of the failing aggregate was never tried, so it cannot overtake the first
        assertEquals(0, row(ids.get(2)).get("attempts"));

        assertEquals(2, new OutboxRelay(jdbcTemplate, new LoggingEventPublisher(), transactionManager).publishBatch());
        assertNull(row(ids.get(0)).get("last_error"));
    }

    @Test
    void cleanupDeletesOnlyEventsPublishedMoreThanSevenDaysAgo() {
        List<Long> ids = writeEvents(TEST_AGGREGATE, TEST_AGGREGATE + 1, TEST_AGGREGATE + 2);
        jdbcTemplate.update("UPDATE outbox_events SET published_at = CURRENT_TIMESTAMP - INTERVAL '8 days' WHERE id = ?", ids.get(0));
        jdbcTemplate.update("UPDATE outbox_events SET published_at = CURRENT_TIMESTAMP - INTERVAL '6 days' WHERE id = ?", ids.get(1));

        assertEquals(1, relay.deleteOldPublished());

        assertEquals(List.of(ids.get(1), ids.get(2)), jdbcTemplate.queryForList(
                "SELECT id FROM outbox_events WHERE aggregate_id >= ? ORDER BY id", Long.class, TEST_AGGREGATE));
    }

    private List<Long> writeEvents(long... aggregateIds) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        for (long aggregateId : aggregateIds) {
            transaction.executeWithoutResult(status -> outboxWriter.write("TEST", aggregateId, "Test.v1", Map.of()));
        }
        return jdbcTemplate.queryForList("SELECT id FROM outbox_events WHERE aggregate_id >= ? ORDER BY id",
                Long.class, TEST_AGGREGATE);
    }

    private List<Map<String, Object>> events(long orderId) {
        return jdbcTemplate.queryForList("SELECT id, event_type, payload::text AS payload FROM outbox_events "
                + "WHERE aggregate_type = 'ORDER' AND aggregate_id = ? ORDER BY id", orderId);
    }

    private int countEvents(long aggregateId) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM outbox_events WHERE aggregate_id = ?", Integer.class, aggregateId);
    }

    private Map<String, Object> row(long id) {
        return jdbcTemplate.queryForMap("SELECT published_at, attempts, last_error FROM outbox_events WHERE id = ?", id);
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
