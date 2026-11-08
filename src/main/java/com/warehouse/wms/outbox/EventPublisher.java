package com.warehouse.wms.outbox;

/** Sends one event to the outside world. Throwing means it was not sent and will be retried. */
public interface EventPublisher {

    void publish(OutboxEvent event);
}
