package com.warehouse.wms.controller;

import com.warehouse.wms.dto.ShipmentDTO;
import com.warehouse.wms.model.Shipment;
import com.warehouse.wms.service.ShipmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/shipments")
@RequiredArgsConstructor
@Tag(name = "Shipment Management", description = "APIs for managing shipments")
public class ShipmentController {

    private final ShipmentService shipmentService;

    @GetMapping
    @Operation(summary = "Get all shipments", description = "Retrieve all shipments")
    public ResponseEntity<List<ShipmentDTO>> getAllShipments() {
        return ResponseEntity.ok(shipmentService.getAllShipments());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get shipment by ID", description = "Retrieve shipment by its ID")
    public ResponseEntity<ShipmentDTO> getShipmentById(@PathVariable Long id) {
        return ResponseEntity.ok(shipmentService.getShipmentById(id));
    }

    @GetMapping("/tracking/{trackingNumber}")
    @Operation(summary = "Track shipment", description = "Track shipment by tracking number")
    public ResponseEntity<ShipmentDTO> getShipmentByTrackingNumber(@PathVariable String trackingNumber) {
        return ResponseEntity.ok(shipmentService.getShipmentByTrackingNumber(trackingNumber));
    }

    @GetMapping("/warehouse/{warehouseId}")
    @Operation(summary = "Get shipments by warehouse", description = "Retrieve all shipments for a warehouse")
    public ResponseEntity<List<ShipmentDTO>> getShipmentsByWarehouse(@PathVariable Long warehouseId) {
        return ResponseEntity.ok(shipmentService.getShipmentsByWarehouse(warehouseId));
    }

    @GetMapping("/status/{status}")
    @Operation(summary = "Get shipments by status", description = "Retrieve shipments by status")
    public ResponseEntity<List<ShipmentDTO>> getShipmentsByStatus(@PathVariable Shipment.ShipmentStatus status) {
        return ResponseEntity.ok(shipmentService.getShipmentsByStatus(status));
    }

    @PostMapping
    @Operation(summary = "Create shipment", description = "Create a new shipment")
    public ResponseEntity<ShipmentDTO> createShipment(@Valid @RequestBody ShipmentDTO shipmentDTO) {
        return new ResponseEntity<>(shipmentService.createShipment(shipmentDTO), HttpStatus.CREATED);
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Update shipment status", description = "Update the status of a shipment")
    public ResponseEntity<ShipmentDTO> updateShipmentStatus(
            @PathVariable Long id,
            @RequestParam Shipment.ShipmentStatus status) {
        return ResponseEntity.ok(shipmentService.updateShipmentStatus(id, status));
    }

    @PostMapping("/{id}/process")
    @Operation(summary = "Process shipment", description = "Process a shipment and update inventory")
    public ResponseEntity<ShipmentDTO> processShipment(@PathVariable Long id) {
        return ResponseEntity.ok(shipmentService.processShipment(id));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete shipment", description = "Delete a shipment")
    public ResponseEntity<Void> deleteShipment(@PathVariable Long id) {
        shipmentService.deleteShipment(id);
        return ResponseEntity.noContent().build();
    }
}