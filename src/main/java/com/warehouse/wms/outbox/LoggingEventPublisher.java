package com.warehouse.wms.outbox;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** The default publisher until a message broker is wired in: it only logs the event. */
@Component
@Slf4j
public class LoggingEventPublisher implements EventPublisher {

    @Override
    public void publish(OutboxEvent event) {
        log.info("Event {} {} for {} {}: {}", event.id(), event.eventType(), event.aggregateType(),
                event.aggregateId(), event.payload());
    }
}
