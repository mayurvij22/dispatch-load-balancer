package com.dispatch.service.impl;

import com.dispatch.dto.DispatchPlanResponse;
import com.dispatch.entity.Order;
import com.dispatch.entity.Vehicle;
import com.dispatch.mapper.DispatchPlanMapper;
import com.dispatch.repository.OrderRepository;
import com.dispatch.repository.VehicleRepository;
import com.dispatch.service.DispatchService;
import com.dispatch.strategy.DispatchResult;
import com.dispatch.strategy.DispatchStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Loads all data with two queries, then delegates to the configured {@link DispatchStrategy}
 * entirely in memory. The plan is computed on demand and not persisted, so it always reflects
 * the latest orders and vehicles.
 */
@Service
public class DispatchServiceImpl implements DispatchService {

    private static final Logger log = LoggerFactory.getLogger(DispatchServiceImpl.class);

    static final String MSG_NO_DATA = "No orders or vehicles available. Add orders and vehicles to generate a plan.";
    static final String MSG_NO_ORDERS = "No orders available to dispatch.";
    static final String MSG_NO_VEHICLES = "No vehicles available. All orders are unassigned.";
    static final String MSG_PARTIAL = "%d order(s) could not be assigned. See unassignedOrders for details.";

    private final OrderRepository orderRepository;
    private final VehicleRepository vehicleRepository;
    private final DispatchStrategy dispatchStrategy;
    private final DispatchPlanMapper dispatchPlanMapper;

    public DispatchServiceImpl(OrderRepository orderRepository,
                               VehicleRepository vehicleRepository,
                               DispatchStrategy dispatchStrategy,
                               DispatchPlanMapper dispatchPlanMapper) {
        this.orderRepository = orderRepository;
        this.vehicleRepository = vehicleRepository;
        this.dispatchStrategy = dispatchStrategy;
        this.dispatchPlanMapper = dispatchPlanMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public DispatchPlanResponse generatePlan() {
        List<Order> orders = orderRepository.findAll();
        List<Vehicle> vehicles = vehicleRepository.findAll();

        DispatchResult result = dispatchStrategy.dispatch(orders, vehicles);
        log.info("Dispatch plan generated: {} order(s), {} vehicle(s), {} unassigned",
                orders.size(), vehicles.size(), result.unassigned().size());

        return dispatchPlanMapper.toResponse(result, messageFor(orders, vehicles, result));
    }

    /** Returns a note for edge cases, or null when every order was assigned. */
    private static String messageFor(List<Order> orders, List<Vehicle> vehicles, DispatchResult result) {
        if (orders.isEmpty() && vehicles.isEmpty()) {
            return MSG_NO_DATA;
        }
        if (orders.isEmpty()) {
            return MSG_NO_ORDERS;
        }
        if (vehicles.isEmpty()) {
            return MSG_NO_VEHICLES;
        }
        if (!result.unassigned().isEmpty()) {
            return MSG_PARTIAL.formatted(result.unassigned().size());
        }
        return null;
    }
}
