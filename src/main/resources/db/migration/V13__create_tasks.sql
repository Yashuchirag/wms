CREATE TABLE tasks (
                       id BIGSERIAL PRIMARY KEY,
                       warehouse_id BIGINT NOT NULL,
                       type VARCHAR(20) NOT NULL,
                       status VARCHAR(20) NOT NULL DEFAULT 'READY',
                       priority INTEGER NOT NULL DEFAULT 0,
                       item_id BIGINT,
                       qty INTEGER,
                       qty_confirmed INTEGER,
                       from_location_id BIGINT,
                       to_location_id BIGINT,
                       lpn_id BIGINT,
                       ref_type VARCHAR(30),
                       ref_id BIGINT,
                       allocation_id BIGINT,
                       wave_id BIGINT,
                       zone_id BIGINT,
                       sequence INTEGER,
                       assigned_user VARCHAR(100),
                       claimed_at TIMESTAMPTZ,
                       completed_at TIMESTAMPTZ,
                       created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                       updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                       CONSTRAINT fk_task_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouses(id) ON DELETE CASCADE,
                       CONSTRAINT fk_task_item FOREIGN KEY (item_id) REFERENCES items(id) ON DELETE RESTRICT,
                       CONSTRAINT fk_task_from_location FOREIGN KEY (from_location_id) REFERENCES locations(id) ON DELETE RESTRICT,
                       CONSTRAINT fk_task_to_location FOREIGN KEY (to_location_id) REFERENCES locations(id) ON DELETE RESTRICT,
                       CONSTRAINT fk_task_lpn FOREIGN KEY (lpn_id) REFERENCES lpns(id) ON DELETE RESTRICT,
                       CONSTRAINT fk_task_allocation FOREIGN KEY (allocation_id) REFERENCES allocations(id) ON DELETE CASCADE,
                       CONSTRAINT fk_task_zone FOREIGN KEY (zone_id) REFERENCES zones(id) ON DELETE RESTRICT,
                       CONSTRAINT chk_task_type CHECK (type IN ('PUTAWAY', 'PICK', 'REPLEN', 'COUNT', 'MOVE')),
                       CONSTRAINT chk_task_status CHECK (status IN ('READY', 'CLAIMED', 'DONE', 'CANCELLED', 'SHORT')),
                       CONSTRAINT chk_task_qty CHECK (qty >= 0 AND qty_confirmed >= 0 AND qty_confirmed <= qty)
);

-- The claim query: the best READY task in a zone
CREATE INDEX idx_task_ready ON tasks(warehouse_id, zone_id, priority DESC, sequence) WHERE status = 'READY';
CREATE INDEX idx_task_ref ON tasks(ref_type, ref_id);
CREATE INDEX idx_task_allocation ON tasks(allocation_id);

CREATE TRIGGER update_tasks_updated_at BEFORE UPDATE ON tasks
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Picks go to a PACK location, so every warehouse needs one
INSERT INTO locations (warehouse_id, code, type)
SELECT w.id, 'PACK-01', 'PACK'
FROM warehouses w
WHERE w.code IN ('WH-002', 'WH-003')
ON CONFLICT (warehouse_id, code) DO NOTHING;
