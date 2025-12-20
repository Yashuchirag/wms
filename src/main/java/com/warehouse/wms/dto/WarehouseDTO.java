package com.warehouse.wms.dto;

import com.warehouse.wms.model.Warehouse;
import jakarta.validation.constraints.NotBlank;

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

    public WarehouseDTO() {}

    public WarehouseDTO(Long id, String code, String name, String address, String city,
                        String state, String zipCode, Double capacity, Warehouse.WarehouseStatus status) {
        this.id = id;
        this.code = code;
        this.name = name;
        this.address = address;
        this.city = city;
        this.state = state;
        this.zipCode = zipCode;
        this.capacity = capacity;
        this.status = status;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }

    public String getState() { return state; }
    public void setState(String state) { this.state = state; }

    public String getZipCode() { return zipCode; }
    public void setZipCode(String zipCode) { this.zipCode = zipCode; }

    public Double getCapacity() { return capacity; }
    public void setCapacity(Double capacity) { this.capacity = capacity; }

    public Warehouse.WarehouseStatus getStatus() { return status; }
    public void setStatus(Warehouse.WarehouseStatus status) { this.status = status; }
}