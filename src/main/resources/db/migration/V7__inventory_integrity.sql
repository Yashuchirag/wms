-- Optimistic-lock version for writes that do not take a row lock
ALTER TABLE inventory ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

-- Never reserve more than is on hand
ALTER TABLE inventory
    ADD CONSTRAINT chk_reserved_within_quantity CHECK (reserved_quantity <= quantity);

-- Treat a NULL zone as one value, so the same item cannot appear twice without a zone
ALTER TABLE inventory DROP CONSTRAINT uq_inventory_item_warehouse_zone;
ALTER TABLE inventory
    ADD CONSTRAINT uq_inventory_item_warehouse_zone UNIQUE NULLS NOT DISTINCT (item_id, warehouse_id, zone_id);

-- An item with stock can no longer be deleted
ALTER TABLE inventory DROP CONSTRAINT fk_inventory_item;
ALTER TABLE inventory
    ADD CONSTRAINT fk_inventory_item FOREIGN KEY (item_id) REFERENCES items(id) ON DELETE RESTRICT;
