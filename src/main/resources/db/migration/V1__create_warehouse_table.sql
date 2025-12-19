CREATE TABLE warehouses (
                            id BIGSERIAL PRIMARY KEY,
                            code VARCHAR(50) NOT NULL UNIQUE,
                            name VARCHAR(255) NOT NULL,
                            address VARCHAR(500),
                            city VARCHAR(100),
                            state VARCHAR(50),
                            zip_code VARCHAR(20),
                            capacity DOUBLE PRECISION,
                            status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
                            created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                            updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                            CONSTRAINT chk_warehouse_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'MAINTENANCE'))
);

CREATE INDEX idx_warehouse_code ON warehouses(code);
CREATE INDEX idx_warehouse_status ON warehouses(status);
CREATE INDEX idx_warehouse_city ON warehouses(city);

-- Insert sample data
INSERT INTO warehouses (code, name, address, city, state, zip_code, capacity, status) VALUES
                                                                                          ('WH-001', 'Main Warehouse', '123 Storage St', 'New York', 'NY', '10001', 100000.0, 'ACTIVE'),
                                                                                          ('WH-002', 'West Coast Distribution', '456 Logistics Ave', 'Los Angeles', 'CA', '90001', 80000.0, 'ACTIVE'),
                                                                                          ('WH-003', 'Midwest Hub', '789 Supply Blvd', 'Chicago', 'IL', '60601', 75000.0, 'ACTIVE');
