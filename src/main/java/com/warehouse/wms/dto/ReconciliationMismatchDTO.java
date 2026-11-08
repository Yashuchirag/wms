package com.warehouse.wms.dto;

/**
 * One (warehouse, item, location) group whose ledger totals differ from its inventory rows.
 * locationId is null for stock that has no location.
 */
public record ReconciliationMismatchDTO(
        Long warehouseId,
        Long itemId,
        Long locationId,
        long ledgerQuantity,
        long balanceQuantity,
        long ledgerReserved,
        long balanceReserved
) {}
