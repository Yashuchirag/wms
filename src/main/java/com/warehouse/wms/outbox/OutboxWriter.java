package com.warehouse.wms.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxWriter {

    private final JdbcTemplate jdbcTemplate;
    private final JsonMapper jsonMapper;

    /**
     * Stores an event in the caller's transaction, so it exists exactly when the change it describes commits.
     * MANDATORY: writing an event outside a business transaction is a bug. The payload gains an eventId (the row id,
     * for consumers to drop duplicates, since delivery is at least once) and an occurredAt.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void write(String aggregateType, Long aggregateId, String eventType, Map<String, Object> payload) {
        Long id = jdbcTemplate.queryForObject("SELECT nextval('outbox_events_id_seq')", Long.class);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("eventId", id);
        body.put("eventType", eventType);
        body.put("occurredAt", Instant.now().toString());
        body.putAll(payload);

        jdbcTemplate.update("INSERT INTO outbox_events (id, aggregate_type, aggregate_id, event_type, payload) "
                        + "VALUES (?, ?, ?, ?, CAST(? AS JSONB))",
                id, aggregateType, aggregateId, eventType, jsonMapper.writeValueAsString(body));
        log.info("Outbox event {} {} for {} {}", id, eventType, aggregateType, aggregateId);
    }
}
