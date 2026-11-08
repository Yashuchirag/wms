package com.warehouse.wms.outbox;

/** One stored event as the relay hands it to a publisher; payload is the JSON text. */
public record OutboxEvent(Long id, String aggregateType, Long aggregateId, String eventType, String payload, int attempts) {
}
