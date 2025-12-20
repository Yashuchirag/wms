package com.warehouse.wms.dto;

import com.warehouse.wms.model.Shipment;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

public class ShipmentDTO {
    private Long id;

    @NotBlank(message = "Tracking number is required")
    private String trackingNumber;

    @NotNull(message = "Warehouse ID is required")
    private Long warehouseId;

    @NotNull(message = "Item ID is required")
    private Long itemId;

    @NotNull(message = "Quantity is required")
    @Min(value = 1, message = "Quantity must be at least 1")
    private Integer quantity;

    @NotNull(message = "Shipment type is required")
    private Shipment.ShipmentType type;

    private Shipment.ShipmentStatus status;
    private String customerName;
    private String shippingAddress;
    private LocalDateTime expectedDelivery;
    private LocalDateTime actualDelivery;

    public ShipmentDTO() {}

    public ShipmentDTO(Long id, String trackingNumber, Long warehouseId, Long itemId, Integer quantity,
                       Shipment.ShipmentType type, Shipment.ShipmentStatus status, String customerName,
                       String shippingAddress, LocalDateTime expectedDelivery, LocalDateTime actualDelivery) {
        this.id = id;
        this.trackingNumber = trackingNumber;
        this.warehouseId = warehouseId;
        this.itemId = itemId;
        this.quantity = quantity;
        this.type = type;
        this.status = status;
        this.customerName = customerName;
        this.shippingAddress = shippingAddress;
        this.expectedDelivery = expectedDelivery;
        this.actualDelivery = actualDelivery;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTrackingNumber() { return trackingNumber; }
    public void setTrackingNumber(String trackingNumber) { this.trackingNumber = trackingNumber; }

    public Long getWarehouseId() { return warehouseId; }
    public void setWarehouseId(Long warehouseId) { this.warehouseId = warehouseId; }

    public Long getItemId() { return itemId; }
    public void setItemId(Long itemId) { this.itemId = itemId; }

    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }

    public Shipment.ShipmentType getType() { return type; }
    public void setType(Shipment.ShipmentType type) { this.type = type; }

    public Shipment.ShipmentStatus getStatus() { return status; }
    public void setStatus(Shipment.ShipmentStatus status) { this.status = status; }

    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }

    public String getShippingAddress() { return shippingAddress; }
    public void setShippingAddress(String shippingAddress) { this.shippingAddress = shippingAddress; }

    public LocalDateTime getExpectedDelivery() { return expectedDelivery; }
    public void setExpectedDelivery(LocalDateTime expectedDelivery) { this.expectedDelivery = expectedDelivery; }

    public LocalDateTime getActualDelivery() { return actualDelivery; }
    public void setActualDelivery(LocalDateTime actualDelivery) { this.actualDelivery = actualDelivery; }
}