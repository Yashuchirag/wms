package com.warehouse.wms.dto;

import com.warehouse.wms.model.Item;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ItemDTO {
    private Long id;

    @NotBlank(message = "SKU is required")
    private String sku;

    @NotBlank(message = "Name is required")
    private String name;

    private String description;
    private String category;
    private BigDecimal price;
    private Double weight;
    private String dimensions;
    private Integer turnoverRate;

    @NotNull(message = "Priority is required")
    private Item.ItemPriority priority;
}
