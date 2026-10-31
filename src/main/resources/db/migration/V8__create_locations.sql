CREATE TABLE locations (
                           id BIGSERIAL PRIMARY KEY,
                           warehouse_id BIGINT NOT NULL,
                           zone_id BIGINT,
                           code VARCHAR(50) NOT NULL,
                           type VARCHAR(20) NOT NULL,
                           pick_sequence INTEGER,
                           max_volume_cm3 DOUBLE PRECISION,
                           max_weight_kg DOUBLE PRECISION,
                           is_active BOOLEAN NOT NULL DEFAULT TRUE,
                           is_mixed_sku_allowed BOOLEAN NOT NULL DEFAULT FALSE,
                           created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                           updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                           CONSTRAINT fk_location_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouses(id) ON DELETE CASCADE,
                           CONSTRAINT fk_location_zone FOREIGN KEY (zone_id) REFERENCES zones(id) ON DELETE RESTRICT,
                           CONSTRAINT uq_location_warehouse_code UNIQUE (warehouse_id, code),
                           CONSTRAINT chk_location_type CHECK (type IN ('RECEIVING', 'PICK_FACE', 'RESERVE', 'STAGING', 'PACK', 'DOCK'))
);

CREATE INDEX idx_location_zone_type ON locations(zone_id, type) WHERE is_active;

CREATE TRIGGER update_locations_updated_at BEFORE UPDATE ON locations
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Three bins per seeded zone: reserve bins in the bulk zone, pick faces elsewhere
INSERT INTO locations (warehouse_id, zone_id, code, type, pick_sequence)
SELECT z.warehouse_id, z.id, z.code || '-0' || n,
       CASE WHEN z.type = 'BULK' THEN 'RESERVE' ELSE 'PICK_FACE' END,
       n * 10
FROM zones z
         CROSS JOIN generate_series(1, 3) AS n
WHERE z.code IN ('Z-A1', 'Z-B1', 'Z-C1', 'Z-A2', 'Z-B2')
ORDER BY z.id, n
ON CONFLICT (warehouse_id, code) DO NOTHING;

-- Receiving and packing locations in the main warehouse, outside any storage zone
INSERT INTO locations (warehouse_id, code, type)
SELECT w.id, l.code, l.type
FROM warehouses w
         CROSS JOIN (VALUES ('RCV-01', 'RECEIVING'), ('PACK-01', 'PACK')) AS l(code, type)
WHERE w.code = 'WH-001'
ON CONFLICT (warehouse_id, code) DO NOTHING;

ALTER TABLE inventory ADD COLUMN location_id BIGINT;
ALTER TABLE inventory
    ADD CONSTRAINT fk_inventory_location FOREIGN KEY (location_id) REFERENCES locations(id) ON DELETE RESTRICT;
CREATE INDEX idx_inventory_location ON inventory(location_id);
