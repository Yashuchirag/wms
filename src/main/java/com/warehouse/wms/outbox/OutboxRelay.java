package com.warehouse.wms.outbox;

import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
@Slf4j
public class OutboxRelay {

    static final int BATCH_SIZE = 100;
    private static final int MAX_ERROR_LENGTH = 2000;

    // SKIP LOCKED lets a second relay instance take other rows instead of waiting
    private static final String CLAIM_SQL = """
            SELECT id, aggregate_type, aggregate_id, event_type, payload::text AS payload, attempts
            FROM outbox_events
            WHERE published_at IS NULL
            ORDER BY id
            LIMIT ?
            FOR UPDATE SKIP LOCKED
            """;

    private final JdbcTemplate jdbcTemplate;
    private final EventPublisher publisher;
    private final TransactionTemplate transactionTemplate;

    public OutboxRelay(JdbcTemplate jdbcTemplate, EventPublisher publisher, PlatformTransactionManager transactionManager) {
        this.jdbcTemplate = jdbcTemplate;
        this.publisher = publisher;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /**
     * Publishes up to one batch of unsent events in id order and returns how many were published. A failed event
     * keeps its row (attempts and last_error record why) and is retried on the next run; later events of the same
     * aggregate wait in this batch, so each aggregate's events go out in order.
     */
    public int publishBatch() {
        Integer published = transactionTemplate.execute(status -> {
            List<OutboxEvent> batch = jdbcTemplate.query(CLAIM_SQL, (rs, rowNum) -> new OutboxEvent(
                    rs.getLong("id"), rs.getString("aggregate_type"), rs.getLong("aggregate_id"),
                    rs.getString("event_type"), rs.getString("payload"), rs.getInt("attempts")), BATCH_SIZE);

            Set<String> blocked = new HashSet<>();
            int sent = 0;
            for (OutboxEvent event : batch) {
                String aggregate = event.aggregateType() + ":" + event.aggregateId();
                if (blocked.contains(aggregate)) {
                    continue;
                }
                try {
                    publisher.publish(event);
                    jdbcTemplate.update("UPDATE outbox_events SET published_at = CURRENT_TIMESTAMP, "
                            + "attempts = attempts + 1, last_error = NULL WHERE id = ?", event.id());
                    sent++;
                } catch (RuntimeException e) {
                    blocked.add(aggregate);
                    log.warn("Publishing event {} failed (attempt {}): {}", event.id(), event.attempts() + 1, e.toString());
                    jdbcTemplate.update("UPDATE outbox_events SET attempts = attempts + 1, last_error = ? WHERE id = ?",
                            truncate(e.toString()), event.id());
                }
            }
            return sent;
        });
        return published == null ? 0 : published;
    }

    /** Deletes events published more than seven days ago and returns how many went. */
    public int deleteOldPublished() {
        int deleted = jdbcTemplate.update(
                "DELETE FROM outbox_events WHERE published_at < CURRENT_TIMESTAMP - INTERVAL '7 days'");
        log.info("Deleted {} published outbox events older than 7 days", deleted);
        return deleted;
    }

    private static String truncate(String message) {
        return message.length() <= MAX_ERROR_LENGTH ? message : message.substring(0, MAX_ERROR_LENGTH);
    }
}
