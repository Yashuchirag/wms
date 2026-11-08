package com.warehouse.wms.repository;

import com.warehouse.wms.model.Task;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TaskRepository extends JpaRepository<Task, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM Task t WHERE t.id = :id")
    Optional<Task> findByIdForUpdate(@Param("id") Long id);

    @Query("SELECT t FROM Task t WHERE t.refType = :refType AND t.refId = :refId ORDER BY t.id")
    List<Task> findByRef(@Param("refType") String refType, @Param("refId") Long refId);
}
