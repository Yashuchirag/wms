package com.warehouse.wms.dto;

import com.warehouse.wms.model.Allocation;
import com.warehouse.wms.model.OutboundOrder;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderDTO {
    private Long id;
    private String externalOrderId;
    private Long warehouseId;
    private String warehouseCode;
    private OutboundOrder.OrderStatus status;
    private Integer priority;
    private String carrier;
    private String serviceLevel;
    private OffsetDateTime shipBy;
    private OrderRequestDTO.ShipTo shipTo;
    private LocalDateTime createdAt;
    private OffsetDateTime shippedAt;
    private List<LineDTO> lines;
    private List<AllocationDTO> allocations;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LineDTO {
        private Integer lineNo;
        private Long itemId;
        private String sku;
        private Integer qtyOrdered;
        private Integer qtyAllocated;
        private Integer qtyPicked;
        private Integer qtyShipped;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AllocationDTO {
        private Long id;
        private Integer lineNo;
        private Long inventoryId;
        private Integer qty;
        private Allocation.AllocationStatus status;
    }
}
