CREATE TABLE outbound_orders (
                                 id BIGSERIAL PRIMARY KEY,
                                 warehouse_id BIGINT NOT NULL,
                                 external_ref VARCHAR(100) NOT NULL,
                                 status VARCHAR(20) NOT NULL DEFAULT 'CREATED',
                                 priority INTEGER NOT NULL DEFAULT 0,
                                 carrier VARCHAR(50),
                                 service_level VARCHAR(50),
                                 ship_by TIMESTAMPTZ,
                                 ship_to_name VARCHAR(255),
                                 ship_to_address JSONB,
                                 wave_id BIGINT,
                                 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                 updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                 shipped_at TIMESTAMPTZ,
                                 CONSTRAINT fk_outbound_order_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouses(id) ON DELETE CASCADE,
                                 CONSTRAINT uq_outbound_order_external_ref UNIQUE (warehouse_id, external_ref),
                                 CONSTRAINT chk_outbound_order_status CHECK (status IN
                                     ('CREATED', 'ALLOCATED', 'SHORT', 'RELEASED', 'PICKING', 'PICKED', 'PACKED', 'SHIPPED', 'CANCELLED'))
);

CREATE INDEX idx_outbound_order_status ON outbound_orders(status);
CREATE INDEX idx_outbound_order_wave ON outbound_orders(wave_id);

CREATE TRIGGER update_outbound_orders_updated_at BEFORE UPDATE ON outbound_orders
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TABLE outbound_lines (
                                id BIGSERIAL PRIMARY KEY,
                                order_id BIGINT NOT NULL,
                                line_no INTEGER NOT NULL,
                                item_id BIGINT NOT NULL,
                                qty_ordered INTEGER NOT NULL,
                                qty_allocated INTEGER NOT NULL DEFAULT 0,
                                qty_picked INTEGER NOT NULL DEFAULT 0,
                                qty_shipped INTEGER NOT NULL DEFAULT 0,
                                CONSTRAINT fk_outbound_line_order FOREIGN KEY (order_id) REFERENCES outbound_orders(id) ON DELETE CASCADE,
                                CONSTRAINT fk_outbound_line_item FOREIGN KEY (item_id) REFERENCES items(id) ON DELETE RESTRICT,
                                CONSTRAINT uq_outbound_line_no UNIQUE (order_id, line_no),
                                CONSTRAINT chk_outbound_line_qty CHECK (qty_ordered > 0 AND qty_allocated >= 0
                                    AND qty_picked >= 0 AND qty_shipped >= 0 AND qty_allocated <= qty_ordered)
);

CREATE INDEX idx_outbound_line_item ON outbound_lines(item_id);
