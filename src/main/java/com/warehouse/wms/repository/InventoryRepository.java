package com.warehouse.wms.repository;

import com.warehouse.wms.model.Inventory;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InventoryRepository extends JpaRepository<Inventory, Long> {

    List<Inventory> findByWarehouseId(Long warehouseId);

    List<Inventory> findByItemId(Long itemId);

    Optional<Inventory> findByItemIdAndWarehouseId(Long itemId, Long warehouseId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM Inventory i WHERE i.id = :id")
    Optional<Inventory> findByIdForUpdate(@Param("id") Long id);

    // Returns ids only, so no unlocked Inventory entity enters the persistence context before findByIdForUpdate
    @Query("SELECT i.id FROM Inventory i WHERE i.item.id = :itemId AND i.warehouse.id = :warehouseId ORDER BY i.id")
    List<Long> findIdsByItemIdAndWarehouseId(@Param("itemId") Long itemId, @Param("warehouseId") Long warehouseId);

    @Query("SELECT i FROM Inventory i WHERE i.availableQuantity <= i.reorderPoint")
    List<Inventory> findLowStockItems();

    @Query("SELECT SUM(i.quantity) FROM Inventory i WHERE i.warehouse.id = :warehouseId")
    Long getTotalInventoryByWarehouse(@Param("warehouseId") Long warehouseId);
}