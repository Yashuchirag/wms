CREATE TABLE zones (
                       id BIGSERIAL PRIMARY KEY,
                       code VARCHAR(50) NOT NULL,
                       name VARCHAR(255) NOT NULL,
                       type VARCHAR(20) NOT NULL,
                       warehouse_id BIGINT NOT NULL,
                       CONSTRAINT fk_zone_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouses(id) ON DELETE CASCADE,
                       CONSTRAINT chk_zone_type CHECK (type IN ('HIGH_DEMAND', 'MEDIUM_DEMAND', 'LOW_DEMAND', 'BULK', 'FRAGILE'))
);

CREATE INDEX idx_zone_warehouse ON zones(warehouse_id);
CREATE INDEX idx_zone_type ON zones(type);
CREATE INDEX idx_zone_code ON zones(code);

-- Insert sample zones
INSERT INTO zones (code, name, type, warehouse_id) VALUES
                                                       ('Z-A1', 'High Demand Zone A', 'HIGH_DEMAND', 1),
                                                       ('Z-B1', 'Medium Demand Zone B', 'MEDIUM_DEMAND', 1),
                                                       ('Z-C1', 'Bulk Storage Zone C', 'BULK', 1),
                                                       ('Z-A2', 'High Demand Zone A', 'HIGH_DEMAND', 2),
                                                       ('Z-B2', 'Fragile Items Zone', 'FRAGILE', 2);