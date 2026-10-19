package com.dispatch.strategy;

import com.dispatch.entity.Order;
import com.dispatch.entity.Vehicle;
import com.dispatch.util.DistanceCalculator;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Greedy nearest-vehicle assignment.
 * <ol>
 *   <li>Orders are processed by priority (HIGH → MEDIUM → LOW), then heaviest first
 *       (first-fit-decreasing packs capacity better), then by orderId for determinism.</li>
 *   <li>Each vehicle tracks its remaining capacity and its position, which starts at the vehicle's
 *       current location and moves to each drop point it is given.</li>
 *   <li>Each order goes to the vehicle, among those with enough remaining capacity, whose current
 *       position is nearest (Haversine). Ties go to the lowest vehicleId.</li>
 *   <li>Orders that fit no vehicle are reported as unassigned with a reason.</li>
 * </ol>
 * Runs in O(n log n + n·m) time and O(n + m) extra space for n orders and m vehicles.
 * The result is a good heuristic, not a global optimum (the underlying problem is a
 * capacitated vehicle-routing problem, which is NP-hard).
 */
@Component
public class GreedyNearestVehicleStrategy implements DispatchStrategy {

    /** Processing order: priority first (enum order), heaviest first, then id for stable output. */
    static final Comparator<Order> DISPATCH_ORDER = Comparator.comparing(Order::getPriority)
            .thenComparing(Comparator.comparingDouble(Order::getPackageWeight).reversed())
            .thenComparing(Order::getOrderId);

    /** Tolerance for floating-point capacity and distance comparisons. */
    private static final double EPSILON = 1e-9;

    static final String REASON_NO_VEHICLES = "No vehicles available";
    static final String REASON_EXCEEDS_ALL_CAPACITIES =
            "Package weight %s exceeds the capacity of every vehicle (largest capacity: %s)";
    static final String REASON_INSUFFICIENT_REMAINING =
            "No vehicle has enough remaining capacity for package weight %s";

    private final DistanceCalculator distanceCalculator;

    public GreedyNearestVehicleStrategy(DistanceCalculator distanceCalculator) {
        this.distanceCalculator = distanceCalculator;
    }

    @Override
    public DispatchResult dispatch(List<Order> orders, List<Vehicle> vehicles) {
        // Sorted by id so the first vehicle found at a given distance wins ties
        List<VehicleRoute> routes = vehicles.stream()
                .sorted(Comparator.comparing(Vehicle::getVehicleId))
                .map(VehicleRoute::new)
                .toList();
        double largestCapacity = vehicles.stream().mapToDouble(Vehicle::getCapacity).max().orElse(0);

        List<Order> queue = new ArrayList<>(orders);
        queue.sort(DISPATCH_ORDER);

        List<UnassignedOrder> unassigned = new ArrayList<>();
        for (Order order : queue) {
            VehicleRoute nearest = findNearestFittingRoute(order, routes);
            if (nearest == null) {
                unassigned.add(new UnassignedOrder(order, reasonFor(order, routes.isEmpty(), largestCapacity)));
            } else {
                nearest.assign(order, distanceFrom(nearest, order));
            }
        }

        List<VehicleAssignment> assignments = routes.stream().map(VehicleRoute::toAssignment).toList();
        return new DispatchResult(assignments, unassigned);
    }

    private VehicleRoute findNearestFittingRoute(Order order, List<VehicleRoute> routes) {
        VehicleRoute nearest = null;
        double nearestDistance = Double.POSITIVE_INFINITY;
        for (VehicleRoute route : routes) {
            if (!route.canFit(order.getPackageWeight())) {
                continue;
            }
            double distance = distanceFrom(route, order);
            // Strictly closer only: on a tie the earlier (lower vehicleId) route is kept
            if (distance < nearestDistance - EPSILON) {
                nearest = route;
                nearestDistance = distance;
            }
        }
        return nearest;
    }

    private double distanceFrom(VehicleRoute route, Order order) {
        return distanceCalculator.distanceKm(route.latitude, route.longitude, order.getLatitude(), order.getLongitude());
    }

    private static String reasonFor(Order order, boolean noVehicles, double largestCapacity) {
        if (noVehicles) {
            return REASON_NO_VEHICLES;
        }
        String weight = formatNumber(order.getPackageWeight());
        if (order.getPackageWeight() > largestCapacity + EPSILON) {
            return REASON_EXCEEDS_ALL_CAPACITIES.formatted(weight, formatNumber(largestCapacity));
        }
        return REASON_INSUFFICIENT_REMAINING.formatted(weight);
    }

    /** Renders 25.0 as "25" and 12.5 as "12.5" for readable messages. */
    private static String formatNumber(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value);
    }

    /**
     * Mutable per-vehicle planning state, local to a single {@link #dispatch} call.
     */
    private static final class VehicleRoute {
        private final Vehicle vehicle;
        private final List<Order> stops = new ArrayList<>();
        private double remainingCapacity;
        private double load;
        private double distanceKm;
        private double latitude;
        private double longitude;

        VehicleRoute(Vehicle vehicle) {
            this.vehicle = vehicle;
            this.remainingCapacity = vehicle.getCapacity();
            this.latitude = vehicle.getCurrentLatitude();
            this.longitude = vehicle.getCurrentLongitude();
        }

        boolean canFit(double weight) {
            return weight <= remainingCapacity + EPSILON;
        }

        void assign(Order order, double legDistanceKm) {
            stops.add(order);
            remainingCapacity -= order.getPackageWeight();
            load += order.getPackageWeight();
            distanceKm += legDistanceKm;
            latitude = order.getLatitude();
            longitude = order.getLongitude();
        }

        VehicleAssignment toAssignment() {
            return new VehicleAssignment(vehicle, stops, load, distanceKm);
        }
    }
}
