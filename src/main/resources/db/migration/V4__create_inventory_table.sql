CREATE TABLE inventory (
                           id BIGSERIAL PRIMARY KEY,
                           item_id BIGINT NOT NULL,
                           warehouse_id BIGINT NOT NULL,
                           zone_id BIGINT,
                           quantity INTEGER NOT NULL DEFAULT 0,
                           reserved_quantity INTEGER NOT NULL DEFAULT 0,
                           available_quantity INTEGER NOT NULL DEFAULT 0,
                           reorder_point INTEGER DEFAULT 10,
                           last_stock_count TIMESTAMP,
                           created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                           updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                           CONSTRAINT fk_inventory_item FOREIGN KEY (item_id) REFERENCES items(id) ON DELETE CASCADE,
                           CONSTRAINT fk_inventory_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouses(id) ON DELETE CASCADE,
                           CONSTRAINT fk_inventory_zone FOREIGN KEY (zone_id) REFERENCES zones(id) ON DELETE SET NULL,
                           CONSTRAINT uq_inventory_item_warehouse_zone UNIQUE (item_id, warehouse_id, zone_id),
                           CONSTRAINT chk_quantity_positive CHECK (quantity >= 0),
                           CONSTRAINT chk_reserved_positive CHECK (reserved_quantity >= 0)
);

CREATE INDEX idx_inventory_item ON inventory(item_id);
CREATE INDEX idx_inventory_warehouse ON inventory(warehouse_id);
CREATE INDEX idx_inventory_zone ON inventory(zone_id);
CREATE INDEX idx_inventory_available ON inventory(available_quantity);

-- Insert sample inventory
INSERT INTO inventory (item_id, warehouse_id, zone_id, quantity, reserved_quantity, available_quantity, reorder_point) VALUES
                                                                                                                           (1, 1, 1, 100, 10, 90, 20),
                                                                                                                           (2, 1, 2, 50, 5, 45, 10),
                                                                                                                           (3, 1, 1, 200, 30, 170, 50),
                                                                                                                           (4, 2, 4, 75, 0, 75, 15),
                                                                                                                           (5, 2, 4, 500, 50, 450, 100);