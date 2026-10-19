package com.dispatch.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Request body of {@code POST /api/dispatch/orders}.
 */
public record OrdersRequest(
        @NotEmpty(message = "orders must contain at least one order")
        List<@NotNull(message = "order must not be null") @Valid OrderRequest> orders
) {
}
