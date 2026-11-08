package com.warehouse.wms.service;

import com.warehouse.wms.dto.InventoryDTO;
import com.warehouse.wms.exception.ResourceNotFoundException;
import com.warehouse.wms.model.Inventory;
import com.warehouse.wms.model.Item;
import com.warehouse.wms.model.Warehouse;
import com.warehouse.wms.repository.InventoryRepository;
import com.warehouse.wms.repository.ItemRepository;
import com.warehouse.wms.repository.WarehouseRepository;
import com.warehouse.wms.repository.ZoneRepository;
import com.warehouse.wms.service.InventoryLedgerService.LedgerRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.modelmapper.ModelMapper;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    @Mock
    private InventoryRepository inventoryRepository;

    @Mock
    private ItemRepository itemRepository;

    @Mock
    private WarehouseRepository warehouseRepository;

    @Mock
    private ZoneRepository zoneRepository;

    @Mock
    private ModelMapper modelMapper;

    @Mock
    private InventoryLedgerService inventoryLedgerService;

    @InjectMocks
    private InventoryService inventoryService;

    private Inventory inventory;
    private InventoryDTO inventoryDTO;
    private Item item;
    private Warehouse warehouse;

    @BeforeEach
    void setUp() {
        item = new Item();
        item.setId(1L);
        item.setSku("SKU-001");
        item.setName("Test Item");

        warehouse = new Warehouse();
        warehouse.setId(1L);
        warehouse.setCode("WH-001");
        warehouse.setName("Test Warehouse");

        inventory = new Inventory();
        inventory.setId(1L);
        inventory.setItem(item);
        inventory.setWarehouse(warehouse);
        inventory.setQuantity(100);
        inventory.setReservedQuantity(10);
        inventory.setAvailableQuantity(90);

        inventoryDTO = new InventoryDTO();
        inventoryDTO.setId(1L);
        inventoryDTO.setItemId(1L);
        inventoryDTO.setWarehouseId(1L);
        inventoryDTO.setQuantity(100);
    }

    @Test
    void testGetInventoryById_Success() {
        when(inventoryRepository.findById(1L)).thenReturn(Optional.of(inventory));
        when(modelMapper.map(inventory, InventoryDTO.class)).thenReturn(inventoryDTO);

        InventoryDTO result = inventoryService.getInventoryById(1L);

        assertNotNull(result);
        assertEquals(1L, result.getId());
        verify(inventoryRepository, times(1)).findById(1L);
    }

    @Test
    void testGetInventoryById_NotFound() {
        when(inventoryRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> {
            inventoryService.getInventoryById(1L);
        });
    }

    @Test
    void testGetAllInventory() {
        List<Inventory> inventories = Arrays.asList(inventory);
        when(inventoryRepository.findAll()).thenReturn(inventories);
        when(modelMapper.map(any(Inventory.class), eq(InventoryDTO.class))).thenReturn(inventoryDTO);

        List<InventoryDTO> result = inventoryService.getAllInventory();

        assertNotNull(result);
        assertEquals(1, result.size());
        verify(inventoryRepository, times(1)).findAll();
    }

    @Test
    void testCreateInventory_Success() {
        when(itemRepository.findById(1L)).thenReturn(Optional.of(item));
        when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));
        when(inventoryRepository.save(any(Inventory.class))).thenReturn(inventory);
        when(modelMapper.map(inventory, InventoryDTO.class)).thenReturn(inventoryDTO);

        InventoryDTO result = inventoryService.createInventory(inventoryDTO);

        assertNotNull(result);
        verify(inventoryRepository, times(1)).save(any(Inventory.class));
        // The opening quantity goes through the ledger, not straight onto the row
        verify(inventoryLedgerService).adjust(1L, 100, "INITIAL", LedgerRef.NONE);
        verify(inventoryLedgerService, never()).allocate(anyLong(), anyInt(), any());
    }

    @Test
    void testAdjustStock_Success() {
        LedgerRef ref = new LedgerRef(null, null, null);
        when(inventoryLedgerService.adjust(1L, 50, "Restock", ref)).thenReturn(inventory);
        when(modelMapper.map(inventory, InventoryDTO.class)).thenReturn(inventoryDTO);

        InventoryDTO result = inventoryService.adjustStock(1L, 50, "Restock", null);

        assertNotNull(result);
        verify(inventoryLedgerService, times(1)).adjust(1L, 50, "Restock", ref);
    }

    @Test
    void testReserveStock_Success() {
        LedgerRef ref = new LedgerRef(null, null, null);
        when(inventoryLedgerService.allocate(1L, 20, ref)).thenReturn(inventory);
        when(modelMapper.map(inventory, InventoryDTO.class)).thenReturn(inventoryDTO);

        InventoryDTO result = inventoryService.reserveStock(1L, 20, null);

        assertNotNull(result);
        verify(inventoryLedgerService, times(1)).allocate(1L, 20, ref);
    }
}
