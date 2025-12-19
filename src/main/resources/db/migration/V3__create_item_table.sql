CREATE TABLE items (
                       id BIGSERIAL PRIMARY KEY,
                       sku VARCHAR(100) NOT NULL UNIQUE,
                       name VARCHAR(255) NOT NULL,
                       description TEXT,
                       category VARCHAR(100),
                       price DECIMAL(10, 2),
                       weight DOUBLE PRECISION,
                       dimensions VARCHAR(100),
                       turnover_rate INTEGER DEFAULT 0,
                       priority VARCHAR(20) NOT NULL DEFAULT 'MEDIUM',
                       created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                       updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                       CONSTRAINT chk_item_priority CHECK (priority IN ('HIGH', 'MEDIUM', 'LOW'))
);

CREATE INDEX idx_item_sku ON items(sku);
CREATE INDEX idx_item_category ON items(category);
CREATE INDEX idx_item_priority ON items(priority);
CREATE INDEX idx_item_turnover ON items(turnover_rate);

-- Insert sample items
INSERT INTO items (sku, name, description, category, price, weight, priority, turnover_rate) VALUES
                                                                                                 ('SKU-001', 'Laptop Computer', 'High-performance laptop', 'Electronics', 999.99, 2.5, 'HIGH', 150),
                                                                                                 ('SKU-002', 'Office Chair', 'Ergonomic office chair', 'Furniture', 299.99, 15.0, 'MEDIUM', 80),
                                                                                                 ('SKU-003', 'Wireless Mouse', 'Bluetooth wireless mouse', 'Electronics', 29.99, 0.2, 'HIGH', 200),
                                                                                                 ('SKU-004', 'Desk Lamp', 'LED desk lamp', 'Furniture', 49.99, 1.5, 'LOW', 45),
                                                                                                 ('SKU-005', 'USB Cable', 'USB-C charging cable', 'Electronics', 9.99, 0.1, 'MEDIUM', 300);