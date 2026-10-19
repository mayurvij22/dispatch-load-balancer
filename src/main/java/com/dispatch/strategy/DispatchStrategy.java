package com.dispatch.strategy;

import com.dispatch.entity.Order;
import com.dispatch.entity.Vehicle;

import java.util.List;

/**
 * Strategy for assigning orders to vehicles (Strategy pattern).
 * Implementations must be pure: no I/O and no mutation of the input entities.
 */
public interface DispatchStrategy {

    /**
     * @param orders   all orders to place; may be empty
     * @param vehicles the available fleet; may be empty
     * @return one {@link VehicleAssignment} per vehicle plus the orders that could not be placed
     */
    DispatchResult dispatch(List<Order> orders, List<Vehicle> vehicles);
}
