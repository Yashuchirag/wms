package com.warehouse.wms.repository;

import com.warehouse.wms.model.Zone;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ZoneRepository extends JpaRepository<Zone, Long> {

    List<Zone> findByWarehouseId(Long warehouseId);

    Optional<Zone> findByCode(String code);

    List<Zone> findByType(Zone.ZoneType type);
}