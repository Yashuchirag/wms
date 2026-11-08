package com.warehouse.wms.service;

import com.warehouse.wms.dto.InventoryDTO;
import com.warehouse.wms.dto.ReconciliationMismatchDTO;
import com.warehouse.wms.exception.ResourceNotFoundException;
import com.warehouse.wms.model.Inventory;
import com.warehouse.wms.model.Item;
import com.warehouse.wms.model.Warehouse;
import com.warehouse.wms.model.Zone;
import com.warehouse.wms.repository.InventoryRepository;
import com.warehouse.wms.repository.ItemRepository;
import com.warehouse.wms.repository.WarehouseRepository;
import com.warehouse.wms.repository.ZoneRepository;
import com.warehouse.wms.service.InventoryLedgerService.LedgerRef;
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
    private final InventoryLedgerService inventoryLedgerService;
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
        // Starts empty; the opening stock and reservation go through the ledger
        inventory.setQuantity(0);
        inventory.setReservedQuantity(0);
        inventory.setReorderPoint(inventoryDTO.getReorderPoint());

        Inventory savedInventory = inventoryRepository.save(inventory);
        inventoryLedgerService.adjust(savedInventory.getId(), inventoryDTO.getQuantity(), "INITIAL", LedgerRef.NONE);
        int reserved = inventoryDTO.getReservedQuantity() != null ? inventoryDTO.getReservedQuantity() : 0;
        if (reserved > 0) {
            inventoryLedgerService.allocate(savedInventory.getId(), reserved, LedgerRef.NONE);
        }
        log.info("Inventory created with id: {}", savedInventory.getId());
        return convertToDTO(savedInventory);
    }

    @CacheEvict(value = "inventory", key = "#id")
    public InventoryDTO updateInventory(Long id, InventoryDTO inventoryDTO) {
        log.info("Updating inventory with id: {}", id);

        Inventory inventory = inventoryRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory not found"));

        // The requested absolute values become ledger entries: release first, then adjust, then reserve
        int newReserved = inventoryDTO.getReservedQuantity() != null ? inventoryDTO.getReservedQuantity() : 0;
        int reservedDelta = newReserved - inventory.getReservedQuantity();
        int quantityDelta = inventoryDTO.getQuantity() - inventory.getQuantity();
        if (reservedDelta < 0) {
            inventoryLedgerService.deallocate(id, -reservedDelta, LedgerRef.NONE);
        }
        inventoryLedgerService.adjust(id, quantityDelta, "MANUAL_UPDATE", LedgerRef.NONE);
        if (reservedDelta > 0) {
            inventoryLedgerService.allocate(id, reservedDelta, LedgerRef.NONE);
        }
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
    public InventoryDTO adjustStock(Long id, Integer adjustment, String reason, String idempotencyKey) {
        log.info("Adjusting stock for inventory id: {} by {}", id, adjustment);
        if (isRepeatedRequest(idempotencyKey)) {
            return currentState(id);
        }

        Inventory inventory = inventoryLedgerService.adjust(id, adjustment, reason,
                new LedgerRef(null, null, idempotencyKey));
        inventory.setLastStockCount(LocalDateTime.now());

        log.info("Stock adjusted. New quantity: {}", inventory.getQuantity());
        return convertToDTO(inventory);
    }

    @CacheEvict(value = "inventory", allEntries = true)
    public InventoryDTO reserveStock(Long id, Integer quantity, String idempotencyKey) {
        log.info("Reserving {} units for inventory id: {}", quantity, id);
        if (isRepeatedRequest(idempotencyKey)) {
            return currentState(id);
        }

        Inventory inventory = inventoryLedgerService.allocate(id, quantity, new LedgerRef(null, null, idempotencyKey));

        log.info("Stock reserved. Reserved quantity: {}", inventory.getReservedQuantity());
        return convertToDTO(inventory);
    }

    @CacheEvict(value = "inventory", key = "#id")
    public void deleteInventory(Long id) {
        log.info("Deleting inventory with id: {}", id);
        Inventory inventory = inventoryRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory not found"));

        // Close the row out in the ledger so it still reconciles after the row is gone
        if (inventory.getReservedQuantity() > 0) {
            inventoryLedgerService.deallocate(id, inventory.getReservedQuantity(), LedgerRef.NONE);
        }
        inventoryLedgerService.adjust(id, -inventory.getQuantity(), "RECORD_DELETED", LedgerRef.NONE);
        inventoryRepository.delete(inventory);
        log.info("Inventory deleted successfully");
    }

    public Long getTotalInventoryByWarehouse(Long warehouseId) {
        return inventoryRepository.getTotalInventoryByWarehouse(warehouseId);
    }

    public List<ReconciliationMismatchDTO> getReconciliationMismatches() {
        log.info("Reconciling the inventory ledger with balances");
        return inventoryLedgerService.findMismatches();
    }

    // Claims the key in this transaction; a repeated key means the request already ran and must change nothing
    private boolean isRepeatedRequest(String idempotencyKey) {
        if (idempotencyKey == null || inventoryLedgerService.claimIdempotencyKey(idempotencyKey)) {
            return false;
        }
        log.info("Idempotency-Key {} already processed; returning current state", idempotencyKey);
        return true;
    }

    private InventoryDTO currentState(Long id) {
        return inventoryRepository.findById(id)
                .map(this::convertToDTO)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory not found"));
    }

    private InventoryDTO convertToDTO(Inventory inventory) {
        InventoryDTO dto = modelMapper.map(inventory, InventoryDTO.class);
        // Computed here because @PreUpdate only refreshes the entity at flush, after this mapping
        dto.setAvailableQuantity(inventory.getQuantity() - inventory.getReservedQuantity());
        dto.setItemSku(inventory.getItem().getSku());
        dto.setItemName(inventory.getItem().getName());
        dto.setWarehouseCode(inventory.getWarehouse().getCode());
        if (inventory.getZone() != null) {
            dto.setZoneName(inventory.getZone().getName());
        }
        return dto;
    }
}