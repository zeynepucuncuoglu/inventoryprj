package com.forecast.order.api;

import com.forecast.order.application.OrderService;
import com.forecast.order.application.dto.OrderResponse;
import com.forecast.order.application.dto.PlaceOrderRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
@Tag(name = "Orders", description = "Order lifecycle management: PENDING → CONFIRMED → SHIPPED → DELIVERED")
public class OrderController {

    private final OrderService orderService;

    @Operation(summary = "Place an order",
            description = "Creates a new order in PENDING status. Publishes an order.placed Kafka event.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Order placed"),
            @ApiResponse(responseCode = "400", description = "Invalid request body")
    })
    @PostMapping
    public ResponseEntity<OrderResponse> placeOrder(@Valid @RequestBody PlaceOrderRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(orderService.placeOrder(request));
    }

    @Operation(summary = "Get an order by ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Order found"),
            @ApiResponse(responseCode = "404", description = "Order not found")
    })
    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> getOrder(
            @Parameter(description = "Order UUID") @PathVariable UUID id) {
        return ResponseEntity.ok(orderService.getOrder(id));
    }

    @Operation(summary = "List orders by customer")
    @ApiResponse(responseCode = "200", description = "Order list")
    @GetMapping
    public ResponseEntity<List<OrderResponse>> getOrdersByCustomer(
            @Parameter(description = "Customer UUID", required = true) @RequestParam UUID customerId) {
        return ResponseEntity.ok(orderService.getOrdersByCustomer(customerId));
    }

    @Operation(summary = "Confirm an order",
            description = "Transitions order from PENDING to CONFIRMED. Fails if order is not in PENDING status.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Order confirmed"),
            @ApiResponse(responseCode = "409", description = "Order is not in PENDING status"),
            @ApiResponse(responseCode = "404", description = "Order not found")
    })
    @PatchMapping("/{id}/confirm")
    public ResponseEntity<OrderResponse> confirmOrder(
            @Parameter(description = "Order UUID") @PathVariable UUID id) {
        return ResponseEntity.ok(orderService.confirmOrder(id));
    }

    @Operation(summary = "Ship an order",
            description = "Transitions order from CONFIRMED to SHIPPED. Fails if order is not CONFIRMED.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Order shipped"),
            @ApiResponse(responseCode = "409", description = "Order is not in CONFIRMED status"),
            @ApiResponse(responseCode = "404", description = "Order not found")
    })
    @PatchMapping("/{id}/ship")
    public ResponseEntity<OrderResponse> shipOrder(
            @Parameter(description = "Order UUID") @PathVariable UUID id) {
        return ResponseEntity.ok(orderService.shipOrder(id));
    }

    @Operation(summary = "Cancel an order",
            description = "Cancels an order. Only PENDING and CONFIRMED orders can be cancelled — " +
                    "SHIPPED and DELIVERED orders cannot.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Order cancelled"),
            @ApiResponse(responseCode = "409", description = "Order cannot be cancelled in its current status"),
            @ApiResponse(responseCode = "404", description = "Order not found")
    })
    @PatchMapping("/{id}/cancel")
    public ResponseEntity<OrderResponse> cancelOrder(
            @Parameter(description = "Order UUID") @PathVariable UUID id) {
        return ResponseEntity.ok(orderService.cancelOrder(id));
    }
}
