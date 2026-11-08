-- Append-only inventory ledger. No foreign keys: history must outlive the items and locations it mentions.
-- qty is always positive; txn_type gives the direction.
CREATE TABLE inventory_txn (
                               id BIGSERIAL,
                               occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                               warehouse_id BIGINT NOT NULL,
                               txn_type VARCHAR(20) NOT NULL,
                               item_id BIGINT NOT NULL,
                               qty INTEGER NOT NULL,
                               from_location_id BIGINT,
                               to_location_id BIGINT,
                               lot VARCHAR(50),
                               lpn_id BIGINT,
                               ref_type VARCHAR(30),
                               ref_id BIGINT,
                               reason_code VARCHAR(50),
                               user_id VARCHAR(100),
                               device_id VARCHAR(100),
                               idempotency_key VARCHAR(100),
                               CONSTRAINT pk_inventory_txn PRIMARY KEY (id, occurred_at),
                               CONSTRAINT chk_inventory_txn_qty CHECK (qty > 0),
                               CONSTRAINT chk_inventory_txn_type CHECK (txn_type IN
                                   ('RECEIPT', 'MOVE', 'ALLOCATE', 'DEALLOCATE', 'PICK', 'SHIP', 'ADJUST_IN', 'ADJUST_OUT'))
) PARTITION BY RANGE (occurred_at);

-- Monthly partitions for the month this migration runs in and the next two; anything later lands in the default
DO $$
DECLARE
    month_start DATE := date_trunc('month', CURRENT_DATE)::DATE;
    partition_start DATE;
BEGIN
    FOR i IN 0..2 LOOP
        partition_start := (month_start + make_interval(months => i))::DATE;
        EXECUTE format('CREATE TABLE inventory_txn_%s PARTITION OF inventory_txn FOR VALUES FROM (%L) TO (%L)',
                       to_char(partition_start, 'YYYY_MM'), partition_start,
                       (partition_start + INTERVAL '1 month')::DATE);
    END LOOP;
END $$;

CREATE TABLE inventory_txn_default PARTITION OF inventory_txn DEFAULT;

CREATE INDEX idx_inventory_txn_item_occurred ON inventory_txn(item_id, occurred_at);
CREATE INDEX idx_inventory_txn_ref ON inventory_txn(ref_type, ref_id);

CREATE TABLE processed_requests (
                                    idempotency_key VARCHAR(100) PRIMARY KEY,
                                    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- A balance row is now one item at one location, so stock received into a bin can sit beside a zone-level row
ALTER TABLE inventory DROP CONSTRAINT uq_inventory_item_warehouse_zone;
ALTER TABLE inventory
    ADD CONSTRAINT uq_inventory_balance UNIQUE NULLS NOT DISTINCT (item_id, warehouse_id, zone_id, location_id);

-- Opening balances, so the ledger reconciles with the stock that existed before it
INSERT INTO inventory_txn (warehouse_id, txn_type, item_id, qty, to_location_id, reason_code)
SELECT warehouse_id, 'ADJUST_IN', item_id, quantity, location_id, 'OPENING_BALANCE'
FROM inventory
WHERE quantity > 0;

INSERT INTO inventory_txn (warehouse_id, txn_type, item_id, qty, from_location_id, reason_code)
SELECT warehouse_id, 'ALLOCATE', item_id, reserved_quantity, location_id, 'OPENING_BALANCE'
FROM inventory
WHERE reserved_quantity > 0;

-- Every warehouse needs a receiving location for inbound shipments
INSERT INTO locations (warehouse_id, code, type)
SELECT w.id, 'RCV-01', 'RECEIVING'
FROM warehouses w
WHERE w.code IN ('WH-002', 'WH-003')
ON CONFLICT (warehouse_id, code) DO NOTHING;

-- Inbound shipments end in RECEIVED
ALTER TABLE shipments DROP CONSTRAINT chk_shipment_status;
ALTER TABLE shipments
    ADD CONSTRAINT chk_shipment_status CHECK (status IN ('PENDING', 'PROCESSING', 'RECEIVED', 'SHIPPED', 'DELIVERED', 'CANCELLED'));
