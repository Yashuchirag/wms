package com.warehouse.wms.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class InventoryDTO {
    private Long id;

    @NotNull(message = "Item ID is required")
    private Long itemId;

    @NotNull(message = "Warehouse ID is required")
    private Long warehouseId;

    private Long zoneId;

    @NotNull(message = "Quantity is required")
    @Min(value = 0, message = "Quantity must be positive")
    private Integer quantity;

    private Integer reservedQuantity;
    private Integer availableQuantity;
    private Integer reorderPoint;
    private String itemSku;
    private String itemName;
    private String warehouseCode;
    private String zoneName;
}