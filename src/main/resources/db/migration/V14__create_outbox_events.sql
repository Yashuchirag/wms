-- Events written in the same transaction as the change they describe, published later by the relay.
-- No foreign keys: an event outlives the record it describes.
CREATE TABLE outbox_events (
                               id BIGSERIAL PRIMARY KEY,
                               aggregate_type VARCHAR(50) NOT NULL,
                               aggregate_id BIGINT NOT NULL,
                               event_type VARCHAR(100) NOT NULL,
                               payload JSONB NOT NULL,
                               created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                               published_at TIMESTAMPTZ,
                               attempts INTEGER NOT NULL DEFAULT 0,
                               last_error TEXT
);

-- The relay's query: unsent rows in id order
CREATE INDEX idx_outbox_unpublished ON outbox_events(id) WHERE published_at IS NULL;
-- The cleanup's query: rows published long enough ago
CREATE INDEX idx_outbox_published_at ON outbox_events(published_at) WHERE published_at IS NOT NULL;
