package com.warehouse.wms.dto;

import com.warehouse.wms.model.Zone;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ZoneDTO {
    private Long id;

    @NotBlank(message = "Code is required")
    private String code;

    @NotBlank(message = "Name is required")
    private String name;

    @NotNull(message = "Type is required")
    private Zone.ZoneType type;

    @NotNull(message = "Warehouse ID is required")
    private Long warehouseId;

    private String warehouseName;
}