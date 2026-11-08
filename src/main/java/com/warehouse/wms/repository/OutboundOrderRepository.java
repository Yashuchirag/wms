package com.warehouse.wms.repository;

import com.warehouse.wms.model.OutboundOrder;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OutboundOrderRepository extends JpaRepository<OutboundOrder, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM OutboundOrder o WHERE o.id = :id")
    Optional<OutboundOrder> findByIdForUpdate(@Param("id") Long id);

    Optional<OutboundOrder> findByWarehouseIdAndExternalRef(Long warehouseId, String externalRef);

    @Query("SELECT o FROM OutboundOrder o WHERE (:status IS NULL OR o.status = :status) ORDER BY o.id")
    List<OutboundOrder> search(@Param("status") OutboundOrder.OrderStatus status);
}
