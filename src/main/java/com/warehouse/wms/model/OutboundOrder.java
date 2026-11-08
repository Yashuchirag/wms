package com.warehouse.wms.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

// @Getter/@Setter rather than @Data: the order and its lines refer to each other, so @Data's hashCode and toString would recurse
@Entity
@Table(name = "outbound_orders")
@Getter
@Setter
@NoArgsConstructor
public class OutboundOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private Warehouse warehouse;

    @Column(name = "external_ref", nullable = false)
    private String externalRef;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    @Column(nullable = false)
    private Integer priority;

    private String carrier;

    @Column(name = "service_level")
    private String serviceLevel;

    @Column(name = "ship_by")
    private OffsetDateTime shipBy;

    @Column(name = "ship_to_name")
    private String shipToName;

    // JSONB, written once by OrderService's insert; read here as text
    @Column(name = "ship_to_address", insertable = false, updatable = false)
    private String shipToAddress;

    @Column(name = "wave_id")
    private Long waveId;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    @Column(name = "shipped_at")
    private OffsetDateTime shippedAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL)
    @OrderBy("lineNo")
    private List<OutboundLine> lines = new ArrayList<>();

    public enum OrderStatus {
        CREATED, ALLOCATED, SHORT, RELEASED, PICKING, PICKED, PACKED, SHIPPED, CANCELLED;

        // Cancelling is allowed until picking starts. SHORT -> SHORT is a re-allocation that is still short.
        private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED = Map.of(
                CREATED, Set.of(ALLOCATED, SHORT, CANCELLED),
                SHORT, Set.of(ALLOCATED, SHORT, CANCELLED),
                ALLOCATED, Set.of(RELEASED, CANCELLED),
                RELEASED, Set.of(PICKING, CANCELLED),
                PICKING, Set.of(PICKED),
                PICKED, Set.of(PACKED),
                PACKED, Set.of(SHIPPED));

        public boolean canTransitionTo(OrderStatus next) {
            return ALLOWED.getOrDefault(this, Set.of()).contains(next);
        }
    }
}
