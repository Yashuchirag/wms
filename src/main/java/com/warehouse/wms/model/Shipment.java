package com.warehouse.wms.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "shipments")
@Data
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class Shipment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String trackingNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "warehouse_id")
    private Warehouse warehouse;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id")
    private Item item;

    @Column(nullable = false)
    private Integer quantity;

    @Enumerated(EnumType.STRING)
    private ShipmentType type;

    @Enumerated(EnumType.STRING)
    private ShipmentStatus status;

    private String customerName;
    private String shippingAddress;

    @Column(name = "expected_delivery")
    private LocalDateTime expectedDelivery;

    @Column(name = "actual_delivery")
    private LocalDateTime actualDelivery;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    public enum ShipmentType {
        INBOUND, OUTBOUND
    }

    public enum ShipmentStatus {
        PENDING, PROCESSING, SHIPPED, DELIVERED, CANCELLED
    }
}