package com.dispatch.mapper;

import com.dispatch.dto.DispatchPlanResponse;
import com.dispatch.dto.UnassignedOrderResponse;
import com.dispatch.dto.VehiclePlanResponse;
import com.dispatch.strategy.DispatchResult;
import com.dispatch.strategy.VehicleAssignment;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Converts a {@link DispatchResult} into the API response shape.
 */
@Component
public class DispatchPlanMapper {

    private final OrderMapper orderMapper;

    public DispatchPlanMapper(OrderMapper orderMapper) {
        this.orderMapper = orderMapper;
    }

    public DispatchPlanResponse toResponse(DispatchResult result, String message) {
        return new DispatchPlanResponse(
                result.assignments().stream().map(this::toVehiclePlan).toList(),
                result.unassigned().stream()
                        .map(u -> new UnassignedOrderResponse(u.order().getOrderId(), u.reason()))
                        .toList(),
                message);
    }

    private VehiclePlanResponse toVehiclePlan(VehicleAssignment assignment) {
        return new VehiclePlanResponse(
                assignment.vehicle().getVehicleId(),
                roundToTwoDecimals(assignment.totalLoad()),
                formatDistance(assignment.totalDistanceKm()),
                assignment.orders().stream().map(orderMapper::toResponse).toList());
    }

    /** Formats a distance as {@code "12.34 km"}; Locale.ROOT keeps the decimal point a '.'. */
    static String formatDistance(double km) {
        return String.format(Locale.ROOT, "%.2f km", km);
    }

    /** Removes floating-point noise such as 0.30000000000000004 from summed weights. */
    static double roundToTwoDecimals(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
