package com.warehouse.wms.repository;

import com.warehouse.wms.model.Allocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AllocationRepository extends JpaRepository<Allocation, Long> {

    @Query("SELECT a FROM Allocation a WHERE a.line.order.id = :orderId ORDER BY a.id")
    List<Allocation> findByOrderId(@Param("orderId") Long orderId);
}
