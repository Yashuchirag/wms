package com.warehouse.wms.service;

import com.warehouse.wms.dto.InventoryDTO;
import com.warehouse.wms.exception.ResourceNotFoundException;
import com.warehouse.wms.model.Inventory;
import com.warehouse.wms.model.Item;
import com.warehouse.wms.model.Warehouse;
import com.warehouse.wms.model.Zone;
import com.warehouse.wms.repository.InventoryRepository;
import com.warehouse.wms.repository.ItemRepository;
import com.warehouse.wms.repository.WarehouseRepository;
import com.warehouse.wms.repository.ZoneRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class InventoryService {

    private final InventoryRepository inventoryRepository;
    private final ItemRepository itemRepository;
    private final WarehouseRepository warehouseRepository;
    private final ZoneRepository zoneRepository;
    private final ModelMapper modelMapper;

    @Cacheable(value = "inventory", key = "#id")
    public InventoryDTO getInventoryById(Long id) {
        log.info("Fetching inventory by id: {}", id);
        Inventory inventory = inventoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory not found with id: " + id));
        return convertToDTO(inventory);
    }

    public List<InventoryDTO> getAllInventory() {
        log.info("Fetching all inventory");
        return inventoryRepository.findAll().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public List<InventoryDTO> getInventoryByWarehouse(Long warehouseId) {
        log.info("Fetching inventory for warehouse: {}", warehouseId);
        return inventoryRepository.findByWarehouseId(warehouseId).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public List<InventoryDTO> getInventoryByItem(Long itemId) {
        log.info("Fetching inventory for item: {}", itemId);
        return inventoryRepository.findByItemId(itemId).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public List<InventoryDTO> getLowStockItems() {
        log.info("Fetching low stock items");
        return inventoryRepository.findLowStockItems().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @CacheEvict(value = "inventory", allEntries = true)
    public InventoryDTO createInventory(InventoryDTO inventoryDTO) {
        log.info("Creating new inventory for item: {} in warehouse: {}",
                inventoryDTO.getItemId(), inventoryDTO.getWarehouseId());

        Item item = itemRepository.findById(inventoryDTO.getItemId())
                .orElseThrow(() -> new ResourceNotFoundException("Item not found"));

        Warehouse warehouse = warehouseRepository.findById(inventoryDTO.getWarehouseId())
                .orElseThrow(() -> new ResourceNotFoundException("Warehouse not found"));

        Zone zone = null;
        if (inventoryDTO.getZoneId() != null) {
            zone = zoneRepository.findById(inventoryDTO.getZoneId())
                    .orElseThrow(() -> new ResourceNotFoundException("Zone not found"));
        }

        Inventory inventory = new Inventory();
        inventory.setItem(item);
        inventory.setWarehouse(warehouse);
        inventory.setZone(zone);
        inventory.setQuantity(inventoryDTO.getQuantity());
        inventory.setReservedQuantity(inventoryDTO.getReservedQuantity() != null ?
                inventoryDTO.getReservedQuantity() : 0);
        inventory.setReorderPoint(inventoryDTO.getReorderPoint());

        Inventory savedInventory = inventoryRepository.save(inventory);
        log.info("Inventory created with id: {}", savedInventory.getId());
        return convertToDTO(savedInventory);
    }

    @CacheEvict(value = "inventory", key = "#id")
    public InventoryDTO updateInventory(Long id, InventoryDTO inventoryDTO) {
        log.info("Updating inventory with id: {}", id);

        Inventory inventory = inventoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory not found"));

        inventory.setQuantity(inventoryDTO.getQuantity());
        inventory.setReservedQuantity(inventoryDTO.getReservedQuantity() != null ?
                inventoryDTO.getReservedQuantity() : 0);
        inventory.setReorderPoint(inventoryDTO.getReorderPoint());

        if (inventoryDTO.getZoneId() != null) {
            Zone zone = zoneRepository.findById(inventoryDTO.getZoneId())
                    .orElseThrow(() -> new ResourceNotFoundException("Zone not found"));
            inventory.setZone(zone);
        }

        Inventory updatedInventory = inventoryRepository.save(inventory);
        log.info("Inventory updated successfully");
        return convertToDTO(updatedInventory);
    }

    @CacheEvict(value = "inventory", allEntries = true)
    public InventoryDTO adjustStock(Long id, Integer adjustment, String reason) {
        log.info("Adjusting stock for inventory id: {} by {}", id, adjustment);

        Inventory inventory = inventoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory not found"));

        Integer newQuantity = inventory.getQuantity() + adjustment;
        if (newQuantity < 0) {
            throw new IllegalArgumentException("Insufficient stock");
        }

        inventory.setQuantity(newQuantity);
        inventory.setLastStockCount(LocalDateTime.now());

        Inventory updatedInventory = inventoryRepository.save(inventory);
        log.info("Stock adjusted. New quantity: {}", newQuantity);
        return convertToDTO(updatedInventory);
    }

    @CacheEvict(value = "inventory", allEntries = true)
    public InventoryDTO reserveStock(Long id, Integer quantity) {
        log.info("Reserving {} units for inventory id: {}", quantity, id);

        Inventory inventory = inventoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory not found"));

        if (inventory.getAvailableQuantity() < quantity) {
            throw new IllegalArgumentException("Insufficient available stock");
        }

        inventory.setReservedQuantity(inventory.getReservedQuantity() + quantity);

        Inventory updatedInventory = inventoryRepository.save(inventory);
        log.info("Stock reserved. Available quantity: {}", updatedInventory.getAvailableQuantity());
        return convertToDTO(updatedInventory);
    }

    @CacheEvict(value = "inventory", key = "#id")
    public void deleteInventory(Long id) {
        log.info("Deleting inventory with id: {}", id);
        if (!inventoryRepository.existsById(id)) {
            throw new ResourceNotFoundException("Inventory not found");
        }
        inventoryRepository.deleteById(id);
        log.info("Inventory deleted successfully");
    }

    public Long getTotalInventoryByWarehouse(Long warehouseId) {
        return inventoryRepository.getTotalInventoryByWarehouse(warehouseId);
    }

    private InventoryDTO convertToDTO(Inventory inventory) {
        InventoryDTO dto = modelMapper.map(inventory, InventoryDTO.class);
        dto.setItemSku(inventory.getItem().getSku());
        dto.setItemName(inventory.getItem().getName());
        dto.setWarehouseCode(inventory.getWarehouse().getCode());
        if (inventory.getZone() != null) {
            dto.setZoneName(inventory.getZone().getName());
        }
        return dto;
    }
}