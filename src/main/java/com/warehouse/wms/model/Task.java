package com.warehouse.wms.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;

// Plain ids rather than relations: tasks are claimed and listed with SQL, and nothing here needs the related entities
@Entity
@Table(name = "tasks")
@Getter
@Setter
@NoArgsConstructor
public class Task {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "warehouse_id", nullable = false)
    private Long warehouseId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TaskType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TaskStatus status;

    @Column(nullable = false)
    private Integer priority;

    @Column(name = "item_id")
    private Long itemId;

    private Integer qty;

    @Column(name = "qty_confirmed")
    private Integer qtyConfirmed;

    @Column(name = "from_location_id")
    private Long fromLocationId;

    @Column(name = "to_location_id")
    private Long toLocationId;

    @Column(name = "ref_type")
    private String refType;

    @Column(name = "ref_id")
    private Long refId;

    @Column(name = "allocation_id")
    private Long allocationId;

    @Column(name = "zone_id")
    private Long zoneId;

    private Integer sequence;

    @Column(name = "assigned_user")
    private String assignedUser;

    @Column(name = "claimed_at")
    private OffsetDateTime claimedAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    public enum TaskType {
        PUTAWAY, PICK, REPLEN, COUNT, MOVE
    }

    public enum TaskStatus {
        READY, CLAIMED, DONE, CANCELLED, SHORT
    }
}
