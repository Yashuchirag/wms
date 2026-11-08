package com.warehouse.wms.repository;

import com.warehouse.wms.model.Location;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LocationRepository extends JpaRepository<Location, Long> {

    @Query("SELECT l FROM Location l WHERE (:zoneId IS NULL OR l.zone.id = :zoneId) "
            + "AND (:type IS NULL OR l.type = :type) ORDER BY l.id")
    List<Location> search(@Param("zoneId") Long zoneId, @Param("type") Location.LocationType type);

    @Query("SELECT l FROM Location l WHERE l.warehouse.id = :warehouseId AND l.type = com.warehouse.wms.model.Location.LocationType.RECEIVING "
            + "AND l.isActive = true "
            + "ORDER BY l.id LIMIT 1")
    Optional<Location> findReceivingLocation(@Param("warehouseId") Long warehouseId);
}
