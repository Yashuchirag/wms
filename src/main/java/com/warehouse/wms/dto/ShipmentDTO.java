package com.warehouse.wms.dto;

import com.warehouse.wms.model.Shipment;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
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
}