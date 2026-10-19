package com.dispatch.strategy;

import com.dispatch.TestFixtures;
import com.dispatch.entity.Order;
import com.dispatch.entity.Priority;
import com.dispatch.entity.Vehicle;
import com.dispatch.util.DistanceCalculator;
import com.dispatch.util.HaversineDistanceCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Runs the strategy on the 30 orders and 5 vehicles from the assignment PDF.
 * Total demand is 645 against a fleet capacity of 500, so some orders must be left unassigned.
 */
class SampleDataDispatchTest {

    private final DistanceCalculator distance = new HaversineDistanceCalculator();
    private final DispatchStrategy strategy = new GreedyNearestVehicleStrategy(distance);

    private List<Order> orders;
    private List<Vehicle> vehicles;
    private DispatchResult result;

    @BeforeEach
    void dispatchSampleData() {
        orders = TestFixtures.sampleOrders();
        vehicles = TestFixtures.sampleVehicles();
        result = strategy.dispatch(orders, vehicles);
    }

    @Test
    @DisplayName("sample data loads 30 orders and 5 vehicles")
    void sampleDataShape() {
        assertThat(orders).hasSize(30);
        assertThat(vehicles).hasSize(5);
        assertThat(result.assignments()).extracting(a -> a.vehicle().getVehicleId())
                .containsExactly("VEH001", "VEH002", "VEH003", "VEH004", "VEH005");
    }

    @Test
    @DisplayName("no vehicle exceeds its capacity")
    void noVehicleOverCapacity() {
        assertThat(result.assignments()).allSatisfy(a ->
                assertThat(a.totalLoad()).isLessThanOrEqualTo(a.vehicle().getCapacity()));
    }

    @Test
    @DisplayName("every order appears exactly once, either assigned or unassigned")
    void everyOrderAccountedForOnce() {
        List<String> assignedIds = result.assignments().stream()
                .flatMap(a -> a.orders().stream()).map(Order::getOrderId).toList();
        List<String> unassignedIds = result.unassigned().stream().map(u -> u.order().getOrderId()).toList();

        List<String> all = Stream.concat(assignedIds.stream(), unassignedIds.stream()).toList();
        assertThat(all).hasSize(30).doesNotHaveDuplicates();
        assertThat(new HashSet<>(all)).isEqualTo(orderIds(orders));
    }

    @Test
    @DisplayName("every HIGH order is assigned (they total 275, well within the 500 fleet capacity)")
    void allHighPriorityAssigned() {
        assertThat(result.unassigned())
                .extracting(u -> u.order().getPriority())
                .doesNotContain(Priority.HIGH);
    }

    @Test
    @DisplayName("on every route, orders were placed in non-increasing priority (all HIGH before any LOW)")
    void highPlacedBeforeLowOnEveryRoute() {
        for (VehicleAssignment assignment : result.assignments()) {
            List<Priority> priorities = assignment.orders().stream().map(Order::getPriority).toList();
            assertThat(priorities).as("stop priorities of %s", assignment.vehicle().getVehicleId()).isSorted();
        }
    }

    @Test
    @DisplayName("every unassigned order is heavier than any vehicle's remaining capacity")
    void unassignedOrdersReallyDoNotFit() {
        double largestRemaining = result.assignments().stream()
                .mapToDouble(a -> a.vehicle().getCapacity() - a.totalLoad())
                .max().orElseThrow();
        assertThat(result.unassigned()).isNotEmpty().allSatisfy(u -> {
            assertThat(u.order().getPackageWeight()).isGreaterThan(largestRemaining);
            assertThat(u.reason()).isNotBlank();
        });
    }

    @Test
    @DisplayName("totals are consistent: load = sum of weights, distance = sum of route legs")
    void totalsAreConsistent() {
        double totalAssignedWeight = 0;
        for (VehicleAssignment assignment : result.assignments()) {
            Vehicle vehicle = assignment.vehicle();
            double load = assignment.orders().stream().mapToDouble(Order::getPackageWeight).sum();
            assertThat(assignment.totalLoad()).isCloseTo(load, within(1e-9));

            double lat = vehicle.getCurrentLatitude();
            double lon = vehicle.getCurrentLongitude();
            double route = 0;
            for (Order stop : assignment.orders()) {
                route += distance.distanceKm(lat, lon, stop.getLatitude(), stop.getLongitude());
                lat = stop.getLatitude();
                lon = stop.getLongitude();
            }
            assertThat(assignment.totalDistanceKm()).isCloseTo(route, within(1e-9));
            totalAssignedWeight += load;
        }

        double totalUnassignedWeight = result.unassigned().stream().mapToDouble(u -> u.order().getPackageWeight()).sum();
        assertThat(totalAssignedWeight + totalUnassignedWeight).isEqualTo(645);
        assertThat(totalAssignedWeight).isLessThanOrEqualTo(500);
    }

    private static Set<String> orderIds(List<Order> orders) {
        Set<String> ids = new HashSet<>();
        orders.forEach(o -> ids.add(o.getOrderId()));
        return ids;
    }
}
