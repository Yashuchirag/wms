CREATE TABLE allocations (
                             id BIGSERIAL PRIMARY KEY,
                             line_id BIGINT NOT NULL,
                             inventory_id BIGINT NOT NULL,
                             qty INTEGER NOT NULL,
                             status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
                             created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                             CONSTRAINT fk_allocation_line FOREIGN KEY (line_id) REFERENCES outbound_lines(id) ON DELETE CASCADE,
                             CONSTRAINT fk_allocation_inventory FOREIGN KEY (inventory_id) REFERENCES inventory(id) ON DELETE RESTRICT,
                             CONSTRAINT chk_allocation_qty CHECK (qty > 0),
                             CONSTRAINT chk_allocation_status CHECK (status IN ('OPEN', 'PICKED', 'CANCELLED'))
);

CREATE INDEX idx_allocation_line ON allocations(line_id);
CREATE INDEX idx_allocation_inventory_open ON allocations(inventory_id) WHERE status = 'OPEN';
