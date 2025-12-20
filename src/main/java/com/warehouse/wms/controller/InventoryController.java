package com.warehouse.wms.controller;

import com.warehouse.wms.dto.InventoryDTO;
import com.warehouse.wms.service.InventoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/inventory")
@Tag(name = "Inventory Management", description = "APIs for managing warehouse inventory")
public class InventoryController {

    private final InventoryService inventoryService;

    @Autowired
    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @GetMapping
    @Operation(summary = "Get all inventory", description = "Retrieve all inventory records")
    public ResponseEntity<List<InventoryDTO>> getAllInventory() {
        return ResponseEntity.ok(inventoryService.getAllInventory());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get inventory by ID", description = "Retrieve inventory by its ID")
    public ResponseEntity<InventoryDTO> getInventoryById(@PathVariable Long id) {
        return ResponseEntity.ok(inventoryService.getInventoryById(id));
    }

    @GetMapping("/warehouse/{warehouseId}")
    @Operation(summary = "Get inventory by warehouse", description = "Retrieve all inventory for a specific warehouse")
    public ResponseEntity<List<InventoryDTO>> getInventoryByWarehouse(@PathVariable Long warehouseId) {
        return ResponseEntity.ok(inventoryService.getInventoryByWarehouse(warehouseId));
    }

    @GetMapping("/item/{itemId}")
    @Operation(summary = "Get inventory by item", description = "Retrieve all inventory for a specific item")
    public ResponseEntity<List<InventoryDTO>> getInventoryByItem(@PathVariable Long itemId) {
        return ResponseEntity.ok(inventoryService.getInventoryByItem(itemId));
    }

    @GetMapping("/low-stock")
    @Operation(summary = "Get low stock items", description = "Retrieve items with stock below reorder point")
    public ResponseEntity<List<InventoryDTO>> getLowStockItems() {
        return ResponseEntity.ok(inventoryService.getLowStockItems());
    }

    @GetMapping("/warehouse/{warehouseId}/total")
    @Operation(summary = "Get total inventory", description = "Get total inventory count for a warehouse")
    public ResponseEntity<Long> getTotalInventory(@PathVariable Long warehouseId) {
        return ResponseEntity.ok(inventoryService.getTotalInventoryByWarehouse(warehouseId));
    }

    @PostMapping
    @Operation(summary = "Create inventory", description = "Create a new inventory record")
    public ResponseEntity<InventoryDTO> createInventory(@Valid @RequestBody InventoryDTO inventoryDTO) {
        return new ResponseEntity<>(inventoryService.createInventory(inventoryDTO), HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update inventory", description = "Update an existing inventory record")
    public ResponseEntity<InventoryDTO> updateInventory(@PathVariable Long id, @Valid @RequestBody InventoryDTO inventoryDTO) {
        return ResponseEntity.ok(inventoryService.updateInventory(id, inventoryDTO));
    }

    @PatchMapping("/{id}/adjust")
    @Operation(summary = "Adjust stock", description = "Adjust inventory quantity")
    public ResponseEntity<InventoryDTO> adjustStock(@PathVariable Long id, @RequestParam Integer adjustment,
                                                    @RequestParam(required = false) String reason) {
        return ResponseEntity.ok(inventoryService.adjustStock(id, adjustment, reason));
    }

    @PatchMapping("/{id}/reserve")
    @Operation(summary = "Reserve stock", description = "Reserve inventory for shipment")
    public ResponseEntity<InventoryDTO> reserveStock(@PathVariable Long id, @RequestParam Integer quantity) {
        return ResponseEntity.ok(inventoryService.reserveStock(id, quantity));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete inventory", description = "Delete an inventory record")
    public ResponseEntity<Void> deleteInventory(@PathVariable Long id) {
        inventoryService.deleteInventory(id);
        return ResponseEntity.noContent().build();
    }
}