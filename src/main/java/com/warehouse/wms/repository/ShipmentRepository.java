package com.warehouse.wms.repository;

import com.warehouse.wms.model.Shipment;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ShipmentRepository extends JpaRepository<Shipment, Long> {

    Optional<Shipment> findByTrackingNumber(String trackingNumber);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Shipment s WHERE s.id = :id")
    Optional<Shipment> findByIdForUpdate(@Param("id") Long id);

    List<Shipment> findByStatus(Shipment.ShipmentStatus status);

    List<Shipment> findByWarehouseId(Long warehouseId);

    List<Shipment> findByType(Shipment.ShipmentType type);

    @Query("SELECT s FROM Shipment s WHERE s.status = 'PENDING' " +
            "AND s.createdAt < :threshold")
    List<Shipment> findPendingShipmentsOlderThan(LocalDateTime threshold);
}