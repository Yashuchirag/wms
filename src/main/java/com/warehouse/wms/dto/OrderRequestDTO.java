package com.warehouse.wms.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/** An order as an order management system sends it. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderRequestDTO {

    @NotBlank(message = "External order ID is required")
    private String externalOrderId;

    @NotBlank(message = "Warehouse code is required")
    private String warehouseCode;

    private OffsetDateTime shipBy;
    private String carrier;
    private String serviceLevel;
    private Integer priority;

    @Valid
    private ShipTo shipTo;

    @NotEmpty(message = "At least one line is required")
    private List<@Valid Line> lines;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ShipTo {
        private String name;
        private Map<String, Object> address;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Line {
        @NotNull(message = "Line number is required")
        @Min(value = 1, message = "Line number must be at least 1")
        private Integer lineNo;

        @NotBlank(message = "SKU is required")
        private String sku;

        @NotNull(message = "Quantity is required")
        @Min(value = 1, message = "Quantity must be at least 1")
        private Integer qty;
    }
}
