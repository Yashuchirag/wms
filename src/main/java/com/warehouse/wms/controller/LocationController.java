package com.warehouse.wms.controller;

import com.warehouse.wms.dto.LocationDTO;
import com.warehouse.wms.model.Location;
import com.warehouse.wms.service.LocationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/locations")
@RequiredArgsConstructor
@Tag(name = "Location Management", description = "APIs for managing storage locations (bins)")
public class LocationController {

    private final LocationService locationService;

    @GetMapping
    @Operation(summary = "Get locations", description = "Retrieve locations, optionally filtered by zone and type")
    public ResponseEntity<List<LocationDTO>> getLocations(
            @RequestParam(required = false) Long zoneId,
            @RequestParam(required = false) Location.LocationType type) {
        return ResponseEntity.ok(locationService.getLocations(zoneId, type));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get location by ID", description = "Retrieve location by its ID")
    public ResponseEntity<LocationDTO> getLocationById(@PathVariable Long id) {
        return ResponseEntity.ok(locationService.getLocationById(id));
    }

    @PostMapping
    @Operation(summary = "Create location", description = "Create a new storage location")
    public ResponseEntity<LocationDTO> createLocation(@Valid @RequestBody LocationDTO locationDTO) {
        return new ResponseEntity<>(locationService.createLocation(locationDTO), HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update location", description = "Update an existing location")
    public ResponseEntity<LocationDTO> updateLocation(
            @PathVariable Long id,
            @Valid @RequestBody LocationDTO locationDTO) {
        return ResponseEntity.ok(locationService.updateLocation(id, locationDTO));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete location", description = "Delete a location that holds no stock")
    public ResponseEntity<Void> deleteLocation(@PathVariable Long id) {
        locationService.deleteLocation(id);
        return ResponseEntity.noContent().build();
    }
}
