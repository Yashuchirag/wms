package com.warehouse.wms.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "outbound_lines")
@Getter
@Setter
@NoArgsConstructor
public class OutboundLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private OutboundOrder order;

    @Column(name = "line_no", nullable = false)
    private Integer lineNo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;

    @Column(name = "qty_ordered", nullable = false)
    private Integer qtyOrdered;

    @Column(name = "qty_allocated", nullable = false)
    private Integer qtyAllocated = 0;

    @Column(name = "qty_picked", nullable = false)
    private Integer qtyPicked = 0;

    @Column(name = "qty_shipped", nullable = false)
    private Integer qtyShipped = 0;
}
