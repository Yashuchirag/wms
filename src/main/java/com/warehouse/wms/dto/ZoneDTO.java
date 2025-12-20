package com.warehouse.wms.dto;

import com.warehouse.wms.model.Zone;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

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

    public ZoneDTO() {}

    public ZoneDTO(Long id, String code, String name, Zone.ZoneType type, Long warehouseId, String warehouseName) {
        this.id = id;
        this.code = code;
        this.name = name;
        this.type = type;
        this.warehouseId = warehouseId;
        this.warehouseName = warehouseName;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Zone.ZoneType getType() { return type; }
    public void setType(Zone.ZoneType type) { this.type = type; }

    public Long getWarehouseId() { return warehouseId; }
    public void setWarehouseId(Long warehouseId) { this.warehouseId = warehouseId; }

    public String getWarehouseName() { return warehouseName; }
    public void setWarehouseName(String warehouseName) { this.warehouseName = warehouseName; }
}