package com.warehouse.wms.service;

import com.warehouse.wms.dto.WarehouseDTO;
import com.warehouse.wms.exception.ResourceNotFoundException;
import com.warehouse.wms.model.Warehouse;
import com.warehouse.wms.repository.WarehouseRepository;
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
public class WarehouseService {

    private final WarehouseRepository warehouseRepository;
    private final ModelMapper modelMapper;

    @Cacheable(value = "warehouses", key = "#id")
    public WarehouseDTO getWarehouseById(Long id) {
        log.info("Fetching warehouse by id: {}", id);
        Warehouse warehouse = warehouseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Warehouse not found with id: " + id));
        return modelMapper.map(warehouse, WarehouseDTO.class);
    }

    @Cacheable(value = "warehouses", key = "#code")
    public WarehouseDTO getWarehouseByCode(String code) {
        log.info("Fetching warehouse by code: {}", code);
        Warehouse warehouse = warehouseRepository.findByCode(code)
                .orElseThrow(() -> new ResourceNotFoundException("Warehouse not found with code: " + code));
        return modelMapper.map(warehouse, WarehouseDTO.class);
    }

    public List<WarehouseDTO> getAllWarehouses() {
        log.info("Fetching all warehouses");
        return warehouseRepository.findAll().stream()
                .map(warehouse -> modelMapper.map(warehouse, WarehouseDTO.class))
                .collect(Collectors.toList());
    }

    public List<WarehouseDTO> getActiveWarehouses() {
        log.info("Fetching active warehouses");
        return warehouseRepository.findByStatus(Warehouse.WarehouseStatus.ACTIVE).stream()
                .map(warehouse -> modelMapper.map(warehouse, WarehouseDTO.class))
                .collect(Collectors.toList());
    }

    @CacheEvict(value = "warehouses", allEntries = true)
    public WarehouseDTO createWarehouse(WarehouseDTO warehouseDTO) {
        log.info("Creating new warehouse with code: {}", warehouseDTO.getCode());

        if (warehouseRepository.findByCode(warehouseDTO.getCode()).isPresent()) {
            throw new IllegalArgumentException("Warehouse with code already exists: " + warehouseDTO.getCode());
        }

        Warehouse warehouse = modelMapper.map(warehouseDTO, Warehouse.class);
        if (warehouse.getStatus() == null) {
            warehouse.setStatus(Warehouse.WarehouseStatus.ACTIVE);
        }

        Warehouse savedWarehouse = warehouseRepository.save(warehouse);
        log.info("Warehouse created with id: {}", savedWarehouse.getId());
        return modelMapper.map(savedWarehouse, WarehouseDTO.class);
    }

    @CacheEvict(value = "warehouses", key = "#id")
    public WarehouseDTO updateWarehouse(Long id, WarehouseDTO warehouseDTO) {
        log.info("Updating warehouse with id: {}", id);

        Warehouse warehouse = warehouseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Warehouse not found"));

        warehouse.setName(warehouseDTO.getName());
        warehouse.setAddress(warehouseDTO.getAddress());
        warehouse.setCity(warehouseDTO.getCity());
        warehouse.setState(warehouseDTO.getState());
        warehouse.setZipCode(warehouseDTO.getZipCode());
        warehouse.setCapacity(warehouseDTO.getCapacity());
        warehouse.setStatus(warehouseDTO.getStatus());

        Warehouse updatedWarehouse = warehouseRepository.save(warehouse);
        log.info("Warehouse updated successfully");
        return modelMapper.map(updatedWarehouse, WarehouseDTO.class);
    }

    @CacheEvict(value = "warehouses", key = "#id")
    public void deleteWarehouse(Long id) {
        log.info("Deleting warehouse with id: {}", id);
        if (!warehouseRepository.existsById(id)) {
            throw new ResourceNotFoundException("Warehouse not found");
        }
        warehouseRepository.deleteById(id);
        log.info("Warehouse deleted successfully");
    }
}