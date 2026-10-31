CREATE TABLE lpns (
                      id BIGSERIAL PRIMARY KEY,
                      warehouse_id BIGINT NOT NULL,
                      code VARCHAR(50) NOT NULL,
                      type VARCHAR(20) NOT NULL,
                      location_id BIGINT,
                      parent_lpn_id BIGINT,
                      status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
                      created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                      updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                      CONSTRAINT fk_lpn_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouses(id) ON DELETE CASCADE,
                      CONSTRAINT fk_lpn_location FOREIGN KEY (location_id) REFERENCES locations(id) ON DELETE RESTRICT,
                      CONSTRAINT fk_lpn_parent FOREIGN KEY (parent_lpn_id) REFERENCES lpns(id) ON DELETE RESTRICT,
                      CONSTRAINT uq_lpn_warehouse_code UNIQUE (warehouse_id, code),
                      CONSTRAINT chk_lpn_type CHECK (type IN ('PALLET', 'CASE', 'TOTE', 'CARTON')),
                      CONSTRAINT chk_lpn_status CHECK (status IN ('ACTIVE', 'CLOSED', 'SHIPPED'))
);

CREATE INDEX idx_lpn_location ON lpns(location_id);
CREATE INDEX idx_lpn_parent ON lpns(parent_lpn_id);

CREATE TRIGGER update_lpns_updated_at BEFORE UPDATE ON lpns
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

ALTER TABLE inventory ADD COLUMN lpn_id BIGINT;
ALTER TABLE inventory
    ADD CONSTRAINT fk_inventory_lpn FOREIGN KEY (lpn_id) REFERENCES lpns(id) ON DELETE RESTRICT;
CREATE INDEX idx_inventory_lpn ON inventory(lpn_id);
