package com.warehouse.wms.dto;

import com.warehouse.wms.model.Location;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LocationDTO {
    private Long id;

    @NotBlank(message = "Code is required")
    private String code;

    @NotNull(message = "Warehouse ID is required")
    private Long warehouseId;

    private Long zoneId;

    @NotNull(message = "Type is required")
    private Location.LocationType type;

    private Integer pickSequence;
    private Double maxVolumeCm3;
    private Double maxWeightKg;
    private Boolean isActive;
    private Boolean isMixedSkuAllowed;
}
