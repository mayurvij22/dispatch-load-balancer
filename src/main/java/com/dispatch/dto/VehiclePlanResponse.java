package com.dispatch.dto;

import java.util.List;

/**
 * One vehicle's route in the dispatch plan. {@code assignedOrders} is in stop order.
 */
public record VehiclePlanResponse(
        String vehicleId,
        double totalLoad,
        String totalDistance,
        List<OrderResponse> assignedOrders
) {
}
