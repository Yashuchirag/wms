package com.warehouse.wms.dto;

import com.warehouse.wms.model.Warehouse;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WarehouseDTO {
    private Long id;

    @NotBlank(message = "Code is required")
    private String code;

    @NotBlank(message = "Name is required")
    private String name;

    private String address;
    private String city;
    private String state;
    private String zipCode;
    private Double capacity;
    private Warehouse.WarehouseStatus status;
}