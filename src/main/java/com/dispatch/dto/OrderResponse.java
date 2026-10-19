package com.dispatch.dto;

import com.dispatch.entity.Priority;

/**
 * An order as it appears inside a vehicle's {@code assignedOrders}.
 */
public record OrderResponse(
        String orderId,
        double latitude,
        double longitude,
        String address,
        double packageWeight,
        Priority priority
) {
}
