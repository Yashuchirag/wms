package com.warehouse.wms.controller;

import com.warehouse.wms.dto.OrderDTO;
import com.warehouse.wms.dto.OrderRequestDTO;
import com.warehouse.wms.model.OutboundOrder;
import com.warehouse.wms.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
@Tag(name = "Order Management", description = "APIs for outbound orders and allocation")
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    @Operation(summary = "Receive order", description = "Create an outbound order; a duplicate externalOrderId returns the existing order with 200")
    public ResponseEntity<OrderDTO> createOrder(@Valid @RequestBody OrderRequestDTO request) {
        OrderService.OrderIntake intake = orderService.createOrder(request);
        return new ResponseEntity<>(intake.order(), intake.created() ? HttpStatus.CREATED : HttpStatus.OK);
    }

    @GetMapping
    @Operation(summary = "Get orders", description = "Retrieve orders, optionally filtered by status")
    public ResponseEntity<List<OrderDTO>> getOrders(@RequestParam(required = false) OutboundOrder.OrderStatus status) {
        return ResponseEntity.ok(orderService.getOrders(status));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get order by ID", description = "Retrieve an order with its lines and allocations")
    public ResponseEntity<OrderDTO> getOrder(@PathVariable Long id) {
        return ResponseEntity.ok(orderService.getOrder(id));
    }

    @PostMapping("/{id}/allocate")
    @Operation(summary = "Allocate order", description = "Reserve stock for what each line still needs; SHORT if not enough")
    public ResponseEntity<OrderDTO> allocateOrder(@PathVariable Long id) {
        return ResponseEntity.ok(orderService.allocateOrder(id));
    }

    @PostMapping("/{id}/release")
    @Operation(summary = "Release order", description = "Create one pick task per allocation of an ALLOCATED order")
    public ResponseEntity<OrderDTO> releaseOrder(@PathVariable Long id) {
        return ResponseEntity.ok(orderService.releaseOrder(id));
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancel order", description = "Cancel an order before picking starts, release its allocations and cancel its open tasks")
    public ResponseEntity<OrderDTO> cancelOrder(@PathVariable Long id) {
        return ResponseEntity.ok(orderService.cancelOrder(id));
    }
}
