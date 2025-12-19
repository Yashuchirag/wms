package com.warehouse.wms.service;

import com.warehouse.wms.dto.ItemDTO;
import com.warehouse.wms.exception.ResourceNotFoundException;
import com.warehouse.wms.model.Item;
import com.warehouse.wms.repository.ItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ItemService {

    private final ItemRepository itemRepository;
    private final ModelMapper modelMapper;

    @Cacheable(value = "items", key = "#id")
    public ItemDTO getItemById(Long id) {
        log.info("Fetching item by id: {}", id);
        Item item = itemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Item not found with id: " + id));
        return modelMapper.map(item, ItemDTO.class);
    }

    @Cacheable(value = "items", key = "#sku")
    public ItemDTO getItemBySku(String sku) {
        log.info("Fetching item by SKU: {}", sku);
        Item item = itemRepository.findBySku(sku)
                .orElseThrow(() -> new ResourceNotFoundException("Item not found with SKU: " + sku));
        return modelMapper.map(item, ItemDTO.class);
    }

    public List<ItemDTO> getAllItems() {
        log.info("Fetching all items");
        return itemRepository.findAll().stream()
                .map(item -> modelMapper.map(item, ItemDTO.class))
                .collect(Collectors.toList());
    }

    public List<ItemDTO> getItemsByCategory(String category) {
        log.info("Fetching items by category: {}", category);
        return itemRepository.findByCategory(category).stream()
                .map(item -> modelMapper.map(item, ItemDTO.class))
                .collect(Collectors.toList());
    }

    public List<ItemDTO> getHighDemandItems() {
        log.info("Fetching high demand items");
        return itemRepository.findByPriority(Item.ItemPriority.HIGH).stream()
                .map(item -> modelMapper.map(item, ItemDTO.class))
                .collect(Collectors.toList());
    }

    @CacheEvict(value = "items", allEntries = true)
    public ItemDTO createItem(ItemDTO itemDTO) {
        log.info("Creating new item with SKU: {}", itemDTO.getSku());

        if (itemRepository.findBySku(itemDTO.getSku()).isPresent()) {
            throw new IllegalArgumentException("Item with SKU already exists: " + itemDTO.getSku());
        }

        Item item = modelMapper.map(itemDTO, Item.class);
        Item savedItem = itemRepository.save(item);
        log.info("Item created with id: {}", savedItem.getId());
        return modelMapper.map(savedItem, ItemDTO.class);
    }

    @CacheEvict(value = "items", key = "#id")
    public ItemDTO updateItem(Long id, ItemDTO itemDTO) {
        log.info("Updating item with id: {}", id);

        Item item = itemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Item not found"));

        item.setName(itemDTO.getName());
        item.setDescription(itemDTO.getDescription());
        item.setCategory(itemDTO.getCategory());
        item.setPrice(itemDTO.getPrice());
        item.setWeight(itemDTO.getWeight());
        item.setDimensions(itemDTO.getDimensions());
        item.setTurnoverRate(itemDTO.getTurnoverRate());
        item.setPriority(itemDTO.getPriority());

        Item updatedItem = itemRepository.save(item);
        log.info("Item updated successfully");
        return modelMapper.map(updatedItem, ItemDTO.class);
    }

    @CacheEvict(value = "items", key = "#id")
    public void deleteItem(Long id) {
        log.info("Deleting item with id: {}", id);
        if (!itemRepository.existsById(id)) {
            throw new ResourceNotFoundException("Item not found");
        }
        itemRepository.deleteById(id);
        log.info("Item deleted successfully");
    }
}