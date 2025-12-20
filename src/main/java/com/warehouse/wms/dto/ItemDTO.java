package com.warehouse.wms.dto;

import com.warehouse.wms.model.Item;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

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

    public ItemDTO() {}

    public ItemDTO(Long id, String sku, String name, String description, String category,
                   BigDecimal price, Double weight, String dimensions, Integer turnoverRate,
                   Item.ItemPriority priority) {
        this.id = id;
        this.sku = sku;
        this.name = name;
        this.description = description;
        this.category = category;
        this.price = price;
        this.weight = weight;
        this.dimensions = dimensions;
        this.turnoverRate = turnoverRate;
        this.priority = priority;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getSku() { return sku; }
    public void setSku(String sku) { this.sku = sku; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }

    public Double getWeight() { return weight; }
    public void setWeight(Double weight) { this.weight = weight; }

    public String getDimensions() { return dimensions; }
    public void setDimensions(String dimensions) { this.dimensions = dimensions; }

    public Integer getTurnoverRate() { return turnoverRate; }
    public void setTurnoverRate(Integer turnoverRate) { this.turnoverRate = turnoverRate; }

    public Item.ItemPriority getPriority() { return priority; }
    public void setPriority(Item.ItemPriority priority) { this.priority = priority; }
}