package com.warehouse.wms.service;

import com.warehouse.wms.dto.LocationDTO;
import com.warehouse.wms.exception.ResourceNotFoundException;
import com.warehouse.wms.model.Location;
import com.warehouse.wms.model.Warehouse;
import com.warehouse.wms.model.Zone;
import com.warehouse.wms.repository.LocationRepository;
import com.warehouse.wms.repository.WarehouseRepository;
import com.warehouse.wms.repository.ZoneRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class LocationService {

    private final LocationRepository locationRepository;
    private final WarehouseRepository warehouseRepository;
    private final ZoneRepository zoneRepository;
    private final ModelMapper modelMapper;

    public LocationDTO getLocationById(Long id) {
        log.info("Fetching location by id: {}", id);
        Location location = locationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Location not found with id: " + id));
        return convertToDTO(location);
    }

    public List<LocationDTO> getLocations(Long zoneId, Location.LocationType type) {
        log.info("Fetching locations for zone: {} and type: {}", zoneId, type);
        return locationRepository.search(zoneId, type).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public LocationDTO createLocation(LocationDTO locationDTO) {
        log.info("Creating new location with code: {} in warehouse: {}",
                locationDTO.getCode(), locationDTO.getWarehouseId());

        Warehouse warehouse = warehouseRepository.findById(locationDTO.getWarehouseId())
                .orElseThrow(() -> new ResourceNotFoundException("Warehouse not found"));

        Location location = new Location();
        location.setWarehouse(warehouse);
        applyChanges(location, locationDTO);

        Location savedLocation = locationRepository.save(location);
        log.info("Location created with id: {}", savedLocation.getId());
        return convertToDTO(savedLocation);
    }

    public LocationDTO updateLocation(Long id, LocationDTO locationDTO) {
        log.info("Updating location with id: {}", id);

        Location location = locationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Location not found"));

        if (!location.getWarehouse().getId().equals(locationDTO.getWarehouseId())) {
            throw new IllegalArgumentException("A location cannot be moved to another warehouse");
        }
        applyChanges(location, locationDTO);

        Location updatedLocation = locationRepository.save(location);
        log.info("Location updated successfully");
        return convertToDTO(updatedLocation);
    }

    public void deleteLocation(Long id) {
        log.info("Deleting location with id: {}", id);
        if (!locationRepository.existsById(id)) {
            throw new ResourceNotFoundException("Location not found");
        }
        locationRepository.deleteById(id);
        log.info("Location deleted successfully");
    }

    private void applyChanges(Location location, LocationDTO locationDTO) {
        Zone zone = null;
        if (locationDTO.getZoneId() != null) {
            zone = zoneRepository.findById(locationDTO.getZoneId())
                    .orElseThrow(() -> new ResourceNotFoundException("Zone not found"));
            if (!zone.getWarehouse().getId().equals(location.getWarehouse().getId())) {
                throw new IllegalArgumentException("Zone " + zone.getCode() + " belongs to another warehouse");
            }
        }

        location.setZone(zone);
        location.setCode(locationDTO.getCode());
        location.setType(locationDTO.getType());
        location.setPickSequence(locationDTO.getPickSequence());
        location.setMaxVolumeCm3(locationDTO.getMaxVolumeCm3());
        location.setMaxWeightKg(locationDTO.getMaxWeightKg());
        location.setIsActive(locationDTO.getIsActive() != null ? locationDTO.getIsActive() : true);
        location.setIsMixedSkuAllowed(locationDTO.getIsMixedSkuAllowed() != null ?
                locationDTO.getIsMixedSkuAllowed() : false);
    }

    private LocationDTO convertToDTO(Location location) {
        LocationDTO dto = modelMapper.map(location, LocationDTO.class);
        dto.setWarehouseId(location.getWarehouse().getId());
        dto.setZoneId(location.getZone() != null ? location.getZone().getId() : null);
        return dto;
    }
}
