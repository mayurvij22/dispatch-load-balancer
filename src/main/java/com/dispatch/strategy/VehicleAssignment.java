package com.dispatch.strategy;

import com.dispatch.entity.Order;
import com.dispatch.entity.Vehicle;

import java.util.List;

/**
 * The route planned for one vehicle.
 *
 * @param vehicle         the vehicle
 * @param orders          assigned orders in stop order
 * @param totalLoad       sum of the assigned package weights
 * @param totalDistanceKm length of start → stop 1 → … → stop n, in kilometres (unrounded)
 */
public record VehicleAssignment(Vehicle vehicle, List<Order> orders, double totalLoad, double totalDistanceKm) {

    public VehicleAssignment {
        orders = List.copyOf(orders);
    }
}
