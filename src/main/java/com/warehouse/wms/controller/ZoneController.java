package com.warehouse.wms.controller;

import com.warehouse.wms.dto.ZoneDTO;
import com.warehouse.wms.model.Zone;
import com.warehouse.wms.service.ZoneService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/zones")
@RequiredArgsConstructor
@Tag(name = "Zone Management", description = "APIs for managing warehouse zones")
public class ZoneController {

    private final ZoneService zoneService;

    @GetMapping
    @Operation(summary = "Get all zones", description = "Retrieve all warehouse zones")
    public ResponseEntity<List<ZoneDTO>> getAllZones() {
        return ResponseEntity.ok(zoneService.getAllZones());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get zone by ID", description = "Retrieve zone by its ID")
    public ResponseEntity<ZoneDTO> getZoneById(@PathVariable Long id) {
        return ResponseEntity.ok(zoneService.getZoneById(id));
    }

    @GetMapping("/code/{code}")
    @Operation(summary = "Get zone by code", description = "Retrieve zone by its code")
    public ResponseEntity<ZoneDTO> getZoneByCode(@PathVariable String code) {
        return ResponseEntity.ok(zoneService.getZoneByCode(code));
    }

    @GetMapping("/warehouse/{warehouseId}")
    @Operation(summary = "Get zones by warehouse", description = "Retrieve all zones for a specific warehouse")
    public ResponseEntity<List<ZoneDTO>> getZonesByWarehouse(@PathVariable Long warehouseId) {
        return ResponseEntity.ok(zoneService.getZonesByWarehouse(warehouseId));
    }

    @GetMapping("/type/{type}")
    @Operation(summary = "Get zones by type", description = "Retrieve zones by type (HIGH_DEMAND, MEDIUM_DEMAND, LOW_DEMAND, BULK, FRAGILE)")
    public ResponseEntity<List<ZoneDTO>> getZonesByType(@PathVariable Zone.ZoneType type) {
        return ResponseEntity.ok(zoneService.getZonesByType(type));
    }

    @PostMapping
    @Operation(summary = "Create zone", description = "Create a new warehouse zone")
    public ResponseEntity<ZoneDTO> createZone(@Valid @RequestBody ZoneDTO zoneDTO) {
        return new ResponseEntity<>(zoneService.createZone(zoneDTO), HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update zone", description = "Update an existing zone")
    public ResponseEntity<ZoneDTO> updateZone(
            @PathVariable Long id,
            @Valid @RequestBody ZoneDTO zoneDTO) {
        return ResponseEntity.ok(zoneService.updateZone(id, zoneDTO));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete zone", description = "Delete a warehouse zone")
    public ResponseEntity<Void> deleteZone(@PathVariable Long id) {
        zoneService.deleteZone(id);
        return ResponseEntity.noContent().build();
    }
}