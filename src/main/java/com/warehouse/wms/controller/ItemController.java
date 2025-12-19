package com.warehouse.wms.controller;

import com.warehouse.wms.dto.ItemDTO;
import com.warehouse.wms.service.ItemService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/items")
@RequiredArgsConstructor
@Tag(name = "Item Management", description = "APIs for managing warehouse items")
public class ItemController {

    private final ItemService itemService;

    @GetMapping
    @Operation(summary = "Get all items", description = "Retrieve all items in the catalog")
    public ResponseEntity<List<ItemDTO>> getAllItems() {
        return ResponseEntity.ok(itemService.getAllItems());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get item by ID", description = "Retrieve item by its ID")
    public ResponseEntity<ItemDTO> getItemById(@PathVariable Long id) {
        return ResponseEntity.ok(itemService.getItemById(id));
    }

    @GetMapping("/sku/{sku}")
    @Operation(summary = "Get item by SKU", description = "Retrieve item by its SKU")
    public ResponseEntity<ItemDTO> getItemBySku(@PathVariable String sku) {
        return ResponseEntity.ok(itemService.getItemBySku(sku));
    }

    @GetMapping("/category/{category}")
    @Operation(summary = "Get items by category", description = "Retrieve all items in a category")
    public ResponseEntity<List<ItemDTO>> getItemsByCategory(@PathVariable String category) {
        return ResponseEntity.ok(itemService.getItemsByCategory(category));
    }

    @GetMapping("/high-demand")
    @Operation(summary = "Get high demand items", description = "Retrieve items marked as high priority")
    public ResponseEntity<List<ItemDTO>> getHighDemandItems() {
        return ResponseEntity.ok(itemService.getHighDemandItems());
    }

    @PostMapping
    @Operation(summary = "Create item", description = "Create a new item in the catalog")
    public ResponseEntity<ItemDTO> createItem(@Valid @RequestBody ItemDTO itemDTO) {
        return new ResponseEntity<>(itemService.createItem(itemDTO), HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update item", description = "Update an existing item")
    public ResponseEntity<ItemDTO> updateItem(
            @PathVariable Long id,
            @Valid @RequestBody ItemDTO itemDTO) {
        return ResponseEntity.ok(itemService.updateItem(id, itemDTO));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete item", description = "Delete an item from the catalog")
    public ResponseEntity<Void> deleteItem(@PathVariable Long id) {
        itemService.deleteItem(id);
        return ResponseEntity.noContent().build();
    }
}