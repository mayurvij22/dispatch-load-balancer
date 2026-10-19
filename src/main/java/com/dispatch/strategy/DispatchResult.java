package com.dispatch.strategy;

import java.util.List;

/**
 * Output of a {@link DispatchStrategy}.
 *
 * @param assignments one entry per vehicle, including vehicles with no orders
 * @param unassigned  orders that could not be placed on any vehicle
 */
public record DispatchResult(List<VehicleAssignment> assignments, List<UnassignedOrder> unassigned) {

    public DispatchResult {
        assignments = List.copyOf(assignments);
        unassigned = List.copyOf(unassigned);
    }
}
