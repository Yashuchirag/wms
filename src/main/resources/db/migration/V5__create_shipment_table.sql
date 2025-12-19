CREATE TABLE shipments (
                           id BIGSERIAL PRIMARY KEY,
                           tracking_number VARCHAR(100) NOT NULL UNIQUE,
                           warehouse_id BIGINT NOT NULL,
                           item_id BIGINT NOT NULL,
                           quantity INTEGER NOT NULL,
                           type VARCHAR(20) NOT NULL,
                           status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
                           customer_name VARCHAR(255),
                           shipping_address TEXT,
                           expected_delivery TIMESTAMP,
                           actual_delivery TIMESTAMP,
                           created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                           updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                           CONSTRAINT fk_shipment_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouses(id) ON DELETE CASCADE,
                           CONSTRAINT fk_shipment_item FOREIGN KEY (item_id) REFERENCES items(id) ON DELETE CASCADE,
                           CONSTRAINT chk_shipment_type CHECK (type IN ('INBOUND', 'OUTBOUND')),
                           CONSTRAINT chk_shipment_status CHECK (status IN ('PENDING', 'PROCESSING', 'SHIPPED', 'DELIVERED', 'CANCELLED')),
                           CONSTRAINT chk_quantity_positive CHECK (quantity > 0)
);

CREATE INDEX idx_shipment_tracking ON shipments(tracking_number);
CREATE INDEX idx_shipment_warehouse ON shipments(warehouse_id);
CREATE INDEX idx_shipment_item ON shipments(item_id);
CREATE INDEX idx_shipment_status ON shipments(status);
CREATE INDEX idx_shipment_type ON shipments(type);
CREATE INDEX idx_shipment_created ON shipments(created_at);

-- Insert sample shipments
INSERT INTO shipments (tracking_number, warehouse_id, item_id, quantity, type, status, customer_name, shipping_address, expected_delivery) VALUES
                                                                                                                                               ('TRK-A1B2C3D4', 1, 1, 5, 'OUTBOUND', 'PROCESSING', 'John Doe', '123 Main St, New York, NY 10001', CURRENT_TIMESTAMP + INTERVAL '3 days'),
                                                                                                                                               ('TRK-E5F6G7H8', 1, 3, 20, 'OUTBOUND', 'SHIPPED', 'Jane Smith', '456 Oak Ave, Brooklyn, NY 11201', CURRENT_TIMESTAMP + INTERVAL '2 days'),
                                                                                                                                               ('TRK-I9J0K1L2', 2, 4, 10, 'INBOUND', 'PENDING', NULL, NULL, NULL);