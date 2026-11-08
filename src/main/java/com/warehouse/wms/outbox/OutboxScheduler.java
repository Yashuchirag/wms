package com.warehouse.wms.outbox;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Runs the relay and the cleanup on a schedule; off in tests, which call the relay directly. */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "wms.outbox.relay.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxScheduler {

    private final OutboxRelay relay;

    @Scheduled(fixedDelayString = "${wms.outbox.relay.poll-interval:1000}")
    public void relay() {
        // Keep going while full batches come back, so a backlog drains without waiting a poll interval per batch
        while (relay.publishBatch() == OutboxRelay.BATCH_SIZE) {
        }
    }

    @Scheduled(cron = "${wms.outbox.cleanup.cron:0 0 3 * * *}")
    public void cleanup() {
        relay.deleteOldPublished();
    }
}
