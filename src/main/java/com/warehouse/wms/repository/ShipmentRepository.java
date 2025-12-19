package com.warehouse.wms.repository;

import com.warehouse.wms.model.Shipment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ShipmentRepository extends JpaRepository<Shipment, Long> {

    Optional<Shipment> findByTrackingNumber(String trackingNumber);

    List<Shipment> findByStatus(Shipment.ShipmentStatus status);

    List<Shipment> findByWarehouseId(Long warehouseId);

    List<Shipment> findByType(Shipment.ShipmentType type);

    @Query("SELECT s FROM Shipment s WHERE s.status = 'PENDING' " +
            "AND s.createdAt < :threshold")
    List<Shipment> findPendingShipmentsOlderThan(LocalDateTime threshold);
}