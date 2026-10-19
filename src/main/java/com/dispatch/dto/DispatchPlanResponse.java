package com.dispatch.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Response of {@code GET /api/dispatch/plan}.
 *
 * @param dispatchPlan     one entry per vehicle (including idle vehicles), sorted by vehicleId
 * @param unassignedOrders orders that did not fit on any vehicle
 * @param message          optional note for edge cases (no orders, no vehicles, unassigned orders); omitted when null
 */
public record DispatchPlanResponse(
        List<VehiclePlanResponse> dispatchPlan,
        List<UnassignedOrderResponse> unassignedOrders,
        @JsonInclude(JsonInclude.Include.NON_NULL) String message
) {
}
