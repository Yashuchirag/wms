package com.warehouse.wms.service;

import com.warehouse.wms.model.OutboundOrder;
import com.warehouse.wms.outbox.OutboxWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The order events other systems receive, written through the outbox in the caller's transaction. */
@Component
@RequiredArgsConstructor
public class OrderEvents {

    public static final String ACCEPTED = "OrderAccepted.v1";
    public static final String ALLOCATED = "OrderAllocated.v1";
    public static final String SHORT = "OrderShort.v1";
    // Not written yet: no step ships an order. Wire it in when PACKED -> SHIPPED exists.
    public static final String SHIPPED = "OrderShipped.v1";

    private final OutboxWriter outboxWriter;

    public void write(OutboundOrder order, String eventType) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderId", order.getId());
        payload.put("externalOrderId", order.getExternalRef());
        payload.put("warehouseCode", order.getWarehouse().getCode());
        payload.put("status", order.getStatus().name());
        List<Map<String, Object>> lines = order.getLines().stream()
                .map(line -> {
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("lineNo", line.getLineNo());
                    entry.put("sku", line.getItem().getSku());
                    entry.put("qtyOrdered", line.getQtyOrdered());
                    entry.put("qtyAllocated", line.getQtyAllocated());
                    return entry;
                })
                .toList();
        payload.put("lines", lines);
        outboxWriter.write("ORDER", order.getId(), eventType, payload);
    }
}
