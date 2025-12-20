package com.warehouse.wms.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

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

    public InventoryDTO() {}

    public InventoryDTO(Long id, Long itemId, Long warehouseId, Long zoneId, Integer quantity,
                        Integer reservedQuantity, Integer availableQuantity, Integer reorderPoint,
                        String itemSku, String itemName, String warehouseCode, String zoneName) {
        this.id = id;
        this.itemId = itemId;
        this.warehouseId = warehouseId;
        this.zoneId = zoneId;
        this.quantity = quantity;
        this.reservedQuantity = reservedQuantity;
        this.availableQuantity = availableQuantity;
        this.reorderPoint = reorderPoint;
        this.itemSku = itemSku;
        this.itemName = itemName;
        this.warehouseCode = warehouseCode;
        this.zoneName = zoneName;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getItemId() { return itemId; }
    public void setItemId(Long itemId) { this.itemId = itemId; }

    public Long getWarehouseId() { return warehouseId; }
    public void setWarehouseId(Long warehouseId) { this.warehouseId = warehouseId; }

    public Long getZoneId() { return zoneId; }
    public void setZoneId(Long zoneId) { this.zoneId = zoneId; }

    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }

    public Integer getReservedQuantity() { return reservedQuantity; }
    public void setReservedQuantity(Integer reservedQuantity) { this.reservedQuantity = reservedQuantity; }

    public Integer getAvailableQuantity() { return availableQuantity; }
    public void setAvailableQuantity(Integer availableQuantity) { this.availableQuantity = availableQuantity; }

    public Integer getReorderPoint() { return reorderPoint; }
    public void setReorderPoint(Integer reorderPoint) { this.reorderPoint = reorderPoint; }

    public String getItemSku() { return itemSku; }
    public void setItemSku(String itemSku) { this.itemSku = itemSku; }

    public String getItemName() { return itemName; }
    public void setItemName(String itemName) { this.itemName = itemName; }

    public String getWarehouseCode() { return warehouseCode; }
    public void setWarehouseCode(String warehouseCode) { this.warehouseCode = warehouseCode; }

    public String getZoneName() { return zoneName; }
    public void setZoneName(String zoneName) { this.zoneName = zoneName; }
}