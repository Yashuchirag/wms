package com.warehouse.wms.repository;

import com.warehouse.wms.model.Location;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LocationRepository extends JpaRepository<Location, Long> {

    @Query("SELECT l FROM Location l WHERE (:zoneId IS NULL OR l.zone.id = :zoneId) "
            + "AND (:type IS NULL OR l.type = :type) ORDER BY l.id")
    List<Location> search(@Param("zoneId") Long zoneId, @Param("type") Location.LocationType type);
}
