package com.warehouse.wms.controller;

import com.warehouse.wms.dto.WarehouseDTO;
import com.warehouse.wms.service.WarehouseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/warehouses")
@RequiredArgsConstructor
@Tag(name = "Warehouse Management", description = "APIs for managing warehouses")
public class WarehouseController {

    private final WarehouseService warehouseService;

    @GetMapping
    @Operation(summary = "Get all warehouses", description = "Retrieve all warehouses")
    public ResponseEntity<List<WarehouseDTO>> getAllWarehouses() {
        return ResponseEntity.ok(warehouseService.getAllWarehouses());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get warehouse by ID", description = "Retrieve warehouse by its ID")
    public ResponseEntity<WarehouseDTO> getWarehouseById(@PathVariable Long id) {
        return ResponseEntity.ok(warehouseService.getWarehouseById(id));
    }

    @GetMapping("/code/{code}")
    @Operation(summary = "Get warehouse by code", description = "Retrieve warehouse by its code")
    public ResponseEntity<WarehouseDTO> getWarehouseByCode(@PathVariable String code) {
        return ResponseEntity.ok(warehouseService.getWarehouseByCode(code));
    }

    @GetMapping("/active")
    @Operation(summary = "Get active warehouses", description = "Retrieve all active warehouses")
    public ResponseEntity<List<WarehouseDTO>> getActiveWarehouses() {
        return ResponseEntity.ok(warehouseService.getActiveWarehouses());
    }

    @PostMapping
    @Operation(summary = "Create warehouse", description = "Create a new warehouse")
    public ResponseEntity<WarehouseDTO> createWarehouse(@Valid @RequestBody WarehouseDTO warehouseDTO) {
        return new ResponseEntity<>(warehouseService.createWarehouse(warehouseDTO), HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update warehouse", description = "Update an existing warehouse")
    public ResponseEntity<WarehouseDTO> updateWarehouse(
            @PathVariable Long id,
            @Valid @RequestBody WarehouseDTO warehouseDTO) {
        return ResponseEntity.ok(warehouseService.updateWarehouse(id, warehouseDTO));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete warehouse", description = "Delete a warehouse")
    public ResponseEntity<Void> deleteWarehouse(@PathVariable Long id) {
        warehouseService.deleteWarehouse(id);
        return ResponseEntity.noContent().build();
    }
}