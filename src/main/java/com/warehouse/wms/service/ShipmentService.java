package com.warehouse.wms.service;

import com.warehouse.wms.dto.ShipmentDTO;
import com.warehouse.wms.exception.ResourceNotFoundException;
import com.warehouse.wms.model.Item;
import com.warehouse.wms.model.Shipment;
import com.warehouse.wms.model.Warehouse;
import com.warehouse.wms.repository.ItemRepository;
import com.warehouse.wms.repository.ShipmentRepository;
import com.warehouse.wms.repository.WarehouseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ShipmentService {

    private final ShipmentRepository shipmentRepository;
    private final WarehouseRepository warehouseRepository;
    private final ItemRepository itemRepository;
    private final InventoryService inventoryService;
    private final ModelMapper modelMapper;

    public ShipmentDTO getShipmentById(Long id) {
        log.info("Fetching shipment by id: {}", id);
        Shipment shipment = shipmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Shipment not found with id: " + id));
        return modelMapper.map(shipment, ShipmentDTO.class);
    }

    public ShipmentDTO getShipmentByTrackingNumber(String trackingNumber) {
        log.info("Fetching shipment by tracking number: {}", trackingNumber);
        Shipment shipment = shipmentRepository.findByTrackingNumber(trackingNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Shipment not found"));
        return modelMapper.map(shipment, ShipmentDTO.class);
    }

    public List<ShipmentDTO> getAllShipments() {
        log.info("Fetching all shipments");
        return shipmentRepository.findAll().stream()
                .map(shipment -> modelMapper.map(shipment, ShipmentDTO.class))
                .collect(Collectors.toList());
    }

    public List<ShipmentDTO> getShipmentsByWarehouse(Long warehouseId) {
        log.info("Fetching shipments for warehouse: {}", warehouseId);
        return shipmentRepository.findByWarehouseId(warehouseId).stream()
                .map(shipment -> modelMapper.map(shipment, ShipmentDTO.class))
                .collect(Collectors.toList());
    }

    public List<ShipmentDTO> getShipmentsByStatus(Shipment.ShipmentStatus status) {
        log.info("Fetching shipments by status: {}", status);
        return shipmentRepository.findByStatus(status).stream()
                .map(shipment -> modelMapper.map(shipment, ShipmentDTO.class))
                .collect(Collectors.toList());
    }

    public ShipmentDTO createShipment(ShipmentDTO shipmentDTO) {
        log.info("Creating new shipment");

        Warehouse warehouse = warehouseRepository.findById(shipmentDTO.getWarehouseId())
                .orElseThrow(() -> new ResourceNotFoundException("Warehouse not found"));

        Item item = itemRepository.findById(shipmentDTO.getItemId())
                .orElseThrow(() -> new ResourceNotFoundException("Item not found"));

        Shipment shipment = modelMapper.map(shipmentDTO, Shipment.class);
        shipment.setWarehouse(warehouse);
        shipment.setItem(item);
        shipment.setStatus(Shipment.ShipmentStatus.PENDING);

        if (shipment.getTrackingNumber() == null || shipment.getTrackingNumber().isEmpty()) {
            shipment.setTrackingNumber(generateTrackingNumber());
        }

        Shipment savedShipment = shipmentRepository.save(shipment);
        log.info("Shipment created with tracking number: {}", savedShipment.getTrackingNumber());
        return modelMapper.map(savedShipment, ShipmentDTO.class);
    }

    public ShipmentDTO updateShipmentStatus(Long id, Shipment.ShipmentStatus status) {
        log.info("Updating shipment status for id: {} to {}", id, status);

        Shipment shipment = shipmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Shipment not found"));

        shipment.setStatus(status);

        if (status == Shipment.ShipmentStatus.DELIVERED) {
            shipment.setActualDelivery(LocalDateTime.now());
        }

        Shipment updatedShipment = shipmentRepository.save(shipment);
        log.info("Shipment status updated successfully");
        return modelMapper.map(updatedShipment, ShipmentDTO.class);
    }

    public ShipmentDTO processShipment(Long id) {
        log.info("Processing shipment id: {}", id);

        Shipment shipment = shipmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Shipment not found"));

        if (shipment.getType() == Shipment.ShipmentType.OUTBOUND) {
            // Reserve inventory for outbound shipment
            var inventory = inventoryService.getInventoryByItem(shipment.getItem().getId())
                    .stream()
                    .filter(inv -> inv.getWarehouseId().equals(shipment.getWarehouse().getId()))
                    .findFirst()
                    .orElseThrow(() -> new ResourceNotFoundException("Inventory not found"));

            inventoryService.reserveStock(inventory.getId(), shipment.getQuantity());
        }

        shipment.setStatus(Shipment.ShipmentStatus.PROCESSING);
        Shipment processedShipment = shipmentRepository.save(shipment);

        log.info("Shipment processed successfully");
        return modelMapper.map(processedShipment, ShipmentDTO.class);
    }

    public void deleteShipment(Long id) {
        log.info("Deleting shipment with id: {}", id);
        if (!shipmentRepository.existsById(id)) {
            throw new ResourceNotFoundException("Shipment not found");
        }
        shipmentRepository.deleteById(id);
        log.info("Shipment deleted successfully");
    }

    private String generateTrackingNumber() {
        return "TRK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}