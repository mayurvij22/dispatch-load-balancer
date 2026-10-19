package com.dispatch.controller;

import com.dispatch.dto.ApiResponse;
import com.dispatch.dto.ErrorResponse;
import com.dispatch.dto.OrdersRequest;
import com.dispatch.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/dispatch/orders", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Orders", description = "Submit and reset delivery orders")
public class OrderController {

    static final String ORDERS_ACCEPTED = "Delivery orders accepted.";

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @Operation(summary = "Submit delivery orders",
            description = "Creates new orders and updates existing ones with the same orderId (upsert).")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Orders accepted"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                    description = "Validation failure, malformed JSON, invalid priority or duplicate orderId",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse> submitOrders(@Valid @RequestBody OrdersRequest request) {
        orderService.upsertOrders(request.orders());
        return ResponseEntity.ok(ApiResponse.success(ORDERS_ACCEPTED));
    }

    @Operation(summary = "Delete all orders")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "All orders deleted")
    @DeleteMapping
    public ResponseEntity<Void> deleteAllOrders() {
        orderService.deleteAllOrders();
        return ResponseEntity.noContent().build();
    }
}
