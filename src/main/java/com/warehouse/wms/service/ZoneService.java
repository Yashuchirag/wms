package com.warehouse.wms.service;

import com.warehouse.wms.dto.ZoneDTO;
import com.warehouse.wms.exception.ResourceNotFoundException;
import com.warehouse.wms.model.Warehouse;
import com.warehouse.wms.model.Zone;
import com.warehouse.wms.repository.WarehouseRepository;
import com.warehouse.wms.repository.ZoneRepository;
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
public class ZoneService {

    private final ZoneRepository zoneRepository;
    private final WarehouseRepository warehouseRepository;
    private final ModelMapper modelMapper;

    @Cacheable(value = "zones", key = "#id")
    public ZoneDTO getZoneById(Long id) {
        log.info("Fetching zone by id: {}", id);
        Zone zone = zoneRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Zone not found with id: " + id));
        return convertToDTO(zone);
    }

    @Cacheable(value = "zones", key = "#code")
    public ZoneDTO getZoneByCode(String code) {
        log.info("Fetching zone by code: {}", code);
        Zone zone = zoneRepository.findByCode(code)
                .orElseThrow(() -> new ResourceNotFoundException("Zone not found with code: " + code));
        return convertToDTO(zone);
    }

    public List<ZoneDTO> getAllZones() {
        log.info("Fetching all zones");
        return zoneRepository.findAll().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public List<ZoneDTO> getZonesByWarehouse(Long warehouseId) {
        log.info("Fetching zones for warehouse: {}", warehouseId);
        return zoneRepository.findByWarehouseId(warehouseId).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public List<ZoneDTO> getZonesByType(Zone.ZoneType type) {
        log.info("Fetching zones by type: {}", type);
        return zoneRepository.findByType(type).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @CacheEvict(value = "zones", allEntries = true)
    public ZoneDTO createZone(ZoneDTO zoneDTO) {
        log.info("Creating new zone with code: {} in warehouse: {}",
                zoneDTO.getCode(), zoneDTO.getWarehouseId());

        Warehouse warehouse = warehouseRepository.findById(zoneDTO.getWarehouseId())
                .orElseThrow(() -> new ResourceNotFoundException("Warehouse not found"));

        Zone zone = new Zone();
        zone.setCode(zoneDTO.getCode());
        zone.setName(zoneDTO.getName());
        zone.setType(zoneDTO.getType());
        zone.setWarehouse(warehouse);

        Zone savedZone = zoneRepository.save(zone);
        log.info("Zone created with id: {}", savedZone.getId());
        return convertToDTO(savedZone);
    }

    @CacheEvict(value = "zones", key = "#id")
    public ZoneDTO updateZone(Long id, ZoneDTO zoneDTO) {
        log.info("Updating zone with id: {}", id);

        Zone zone = zoneRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Zone not found"));

        zone.setCode(zoneDTO.getCode());
        zone.setName(zoneDTO.getName());
        zone.setType(zoneDTO.getType());

        if (zoneDTO.getWarehouseId() != null &&
                !zone.getWarehouse().getId().equals(zoneDTO.getWarehouseId())) {
            Warehouse warehouse = warehouseRepository.findById(zoneDTO.getWarehouseId())
                    .orElseThrow(() -> new ResourceNotFoundException("Warehouse not found"));
            zone.setWarehouse(warehouse);
        }

        Zone updatedZone = zoneRepository.save(zone);
        log.info("Zone updated successfully");
        return convertToDTO(updatedZone);
    }

    @CacheEvict(value = "zones", key = "#id")
    public void deleteZone(Long id) {
        log.info("Deleting zone with id: {}", id);
        if (!zoneRepository.existsById(id)) {
            throw new ResourceNotFoundException("Zone not found");
        }
        zoneRepository.deleteById(id);
        log.info("Zone deleted successfully");
    }

    private ZoneDTO convertToDTO(Zone zone) {
        ZoneDTO dto = modelMapper.map(zone, ZoneDTO.class);
        dto.setWarehouseId(zone.getWarehouse().getId());
        dto.setWarehouseName(zone.getWarehouse().getName());
        return dto;
    }
}