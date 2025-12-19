CREATE TABLE zones (
                       id BIGSERIAL PRIMARY KEY,
                       code VARCHAR(50) NOT NULL,
                       name VARCHAR(100) NOT NULL,
                       type VARCHAR(20),
                       warehouse_id BIGINT,
                       created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                       updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                       CONSTRAINT fk_zone_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouses(id) ON DELETE SET NULL,
                       CONSTRAINT chk_zone_type CHECK (type IN ('HIGH_DEMAND', 'MEDIUM_DEMAND', 'LOW_DEMAND', 'BULK', 'FRAGILE'))
);

-- Indexes
CREATE INDEX idx_zone_code ON zones(code);
CREATE INDEX idx_zone_warehouse ON zones(warehouse_id);
CREATE INDEX idx_zone_type ON zones(type);
CREATE INDEX idx_zone_created ON zones(created_at);