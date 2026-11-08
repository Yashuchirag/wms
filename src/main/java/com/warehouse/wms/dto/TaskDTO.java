package com.warehouse.wms.dto;

import com.warehouse.wms.model.Task;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;

@Data
@NoArgsConstructor
public class TaskDTO {
    private Long id;
    private Long warehouseId;
    private Task.TaskType type;
    private Task.TaskStatus status;
    private Integer priority;
    private Long itemId;
    private Integer qty;
    private Integer qtyConfirmed;
    private Long fromLocationId;
    private String fromLocationCode;
    private Long toLocationId;
    private String toLocationCode;
    private String refType;
    private Long refId;
    private Long allocationId;
    private Long zoneId;
    private Integer sequence;
    private String assignedUser;
    private OffsetDateTime claimedAt;
    private OffsetDateTime completedAt;
    private LocalDateTime createdAt;
}
