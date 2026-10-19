package com.dispatch.strategy;

import com.dispatch.entity.Order;
import com.dispatch.entity.Priority;
import com.dispatch.entity.Vehicle;
import com.dispatch.util.HaversineDistanceCalculator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.dispatch.TestFixtures.CONNAUGHT_PLACE;
import static com.dispatch.TestFixtures.CYBER_HUB_GURGAON;
import static com.dispatch.TestFixtures.KAROL_BAGH;
import static com.dispatch.TestFixtures.NOIDA_SECTOR_18;
import static com.dispatch.TestFixtures.order;
import static com.dispatch.TestFixtures.vehicle;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class GreedyNearestVehicleStrategyTest {

    private final HaversineDistanceCalculator distance = new HaversineDistanceCalculator();
    private final GreedyNearestVehicleStrategy strategy = new GreedyNearestVehicleStrategy(distance);

    private static Map<String, VehicleAssignment> byVehicleId(DispatchResult result) {
        return result.assignments().stream()
                .collect(Collectors.toMap(a -> a.vehicle().getVehicleId(), Function.identity()));
    }

    private static List<String> orderIds(VehicleAssignment assignment) {
        return assignment.orders().stream().map(Order::getOrderId).toList();
    }

    @Nested
    @DisplayName("priority and ordering")
    class Priorities {

        @Test
        @DisplayName("HIGH is assigned before LOW when capacity is tight, regardless of input order")
        void highBeatsLowWhenCapacityIsTight() {
            List<Order> orders = List.of(
                    order("LOW-1", CONNAUGHT_PLACE, 10, Priority.LOW),
                    order("HIGH-1", CONNAUGHT_PLACE, 10, Priority.HIGH));
            List<Vehicle> vehicles = List.of(vehicle("V1", 10, CONNAUGHT_PLACE));

            DispatchResult result = strategy.dispatch(orders, vehicles);

            assertThat(orderIds(result.assignments().get(0))).containsExactly("HIGH-1");
            assertThat(result.unassigned()).extracting(u -> u.order().getOrderId()).containsExactly("LOW-1");
        }

        @Test
        @DisplayName("MEDIUM is preferred over LOW")
        void mediumBeatsLow() {
            List<Order> orders = List.of(
                    order("LOW-1", CONNAUGHT_PLACE, 5, Priority.LOW),
                    order("MED-1", CONNAUGHT_PLACE, 5, Priority.MEDIUM));
            DispatchResult result = strategy.dispatch(orders, List.of(vehicle("V1", 5, CONNAUGHT_PLACE)));

            assertThat(orderIds(result.assignments().get(0))).containsExactly("MED-1");
        }

        @Test
        @DisplayName("within a priority, heavier orders are placed first")
        void heavierFirstWithinPriority() {
            assertThat(GreedyNearestVehicleStrategy.DISPATCH_ORDER.compare(
                    order("A", CONNAUGHT_PLACE, 30, Priority.MEDIUM),
                    order("B", CONNAUGHT_PLACE, 10, Priority.MEDIUM))).isNegative();
            assertThat(GreedyNearestVehicleStrategy.DISPATCH_ORDER.compare(
                    order("A", CONNAUGHT_PLACE, 1, Priority.HIGH),
                    order("B", CONNAUGHT_PLACE, 99, Priority.LOW))).isNegative();
        }

        @Test
        @DisplayName("output is deterministic regardless of input order")
        void deterministicOutput() {
            List<Order> orders = List.of(
                    order("O1", NOIDA_SECTOR_18, 10, Priority.LOW),
                    order("O2", KAROL_BAGH, 10, Priority.HIGH),
                    order("O3", CYBER_HUB_GURGAON, 10, Priority.MEDIUM));
            List<Vehicle> vehicles = List.of(vehicle("V2", 15, NOIDA_SECTOR_18), vehicle("V1", 15, KAROL_BAGH));

            DispatchResult forward = strategy.dispatch(orders, vehicles);
            List<Order> reversedOrders = new ArrayList<>(orders);
            List<Vehicle> reversedVehicles = new ArrayList<>(vehicles);
            Collections.reverse(reversedOrders);
            Collections.reverse(reversedVehicles);
            DispatchResult reversed = strategy.dispatch(reversedOrders, reversedVehicles);

            assertThat(forward).isEqualTo(reversed);
        }
    }

    @Nested
    @DisplayName("capacity")
    class Capacity {

        @Test
        @DisplayName("an order exactly equal to remaining capacity fits")
        void exactCapacityFit() {
            List<Order> orders = List.of(
                    order("O1", CONNAUGHT_PLACE, 60, Priority.HIGH),
                    order("O2", CONNAUGHT_PLACE, 40, Priority.HIGH));
            DispatchResult result = strategy.dispatch(orders, List.of(vehicle("V1", 100, CONNAUGHT_PLACE)));

            VehicleAssignment v1 = result.assignments().get(0);
            assertThat(orderIds(v1)).containsExactly("O1", "O2");
            assertThat(v1.totalLoad()).isEqualTo(100);
            assertThat(result.unassigned()).isEmpty();
        }

        @Test
        @DisplayName("fractional weights summing to capacity still fit despite floating-point error")
        void fractionalExactFit() {
            List<Order> orders = List.of(
                    order("O1", CONNAUGHT_PLACE, 0.1, Priority.HIGH),
                    order("O2", CONNAUGHT_PLACE, 0.2, Priority.HIGH));
            DispatchResult result = strategy.dispatch(orders, List.of(vehicle("V1", 0.3, CONNAUGHT_PLACE)));

            assertThat(result.unassigned()).isEmpty();
        }

        @Test
        @DisplayName("capacity is never exceeded; overflow goes to the next nearest vehicle")
        void capacityNeverExceeded() {
            List<Order> orders = List.of(
                    order("O1", CONNAUGHT_PLACE, 50, Priority.HIGH),
                    order("O2", CONNAUGHT_PLACE, 50, Priority.HIGH),
                    order("O3", CONNAUGHT_PLACE, 50, Priority.HIGH));
            List<Vehicle> vehicles = List.of(
                    vehicle("NEAR", 100, CONNAUGHT_PLACE),
                    vehicle("FAR", 100, NOIDA_SECTOR_18));

            DispatchResult result = strategy.dispatch(orders, vehicles);
            Map<String, VehicleAssignment> plan = byVehicleId(result);

            assertThat(plan.get("NEAR").totalLoad()).isEqualTo(100);
            assertThat(plan.get("FAR").totalLoad()).isEqualTo(50);
            result.assignments().forEach(a ->
                    assertThat(a.totalLoad()).isLessThanOrEqualTo(a.vehicle().getCapacity()));
        }

        @Test
        @DisplayName("an order heavier than every vehicle's capacity is unassigned with a clear reason")
        void tooHeavyForAnyVehicle() {
            List<Order> orders = List.of(order("HEAVY", CONNAUGHT_PLACE, 500, Priority.HIGH));
            List<Vehicle> vehicles = List.of(vehicle("V1", 100, CONNAUGHT_PLACE), vehicle("V2", 120, KAROL_BAGH));

            DispatchResult result = strategy.dispatch(orders, vehicles);

            assertThat(result.unassigned()).singleElement().satisfies(u -> {
                assertThat(u.order().getOrderId()).isEqualTo("HEAVY");
                assertThat(u.reason()).isEqualTo(
                        "Package weight 500 exceeds the capacity of every vehicle (largest capacity: 120)");
            });
            result.assignments().forEach(a -> assertThat(a.orders()).isEmpty());
        }

        @Test
        @DisplayName("an order that fits a vehicle but not its remaining space gets a remaining-capacity reason")
        void insufficientRemainingCapacity() {
            List<Order> orders = List.of(
                    order("FIRST", CONNAUGHT_PLACE, 80, Priority.HIGH),
                    order("SECOND", CONNAUGHT_PLACE, 30.5, Priority.LOW));
            DispatchResult result = strategy.dispatch(orders, List.of(vehicle("V1", 100, CONNAUGHT_PLACE)));

            assertThat(result.unassigned()).singleElement().satisfies(u -> {
                assertThat(u.order().getOrderId()).isEqualTo("SECOND");
                assertThat(u.reason()).isEqualTo("No vehicle has enough remaining capacity for package weight 30.5");
            });
        }

        @Test
        @DisplayName("a lighter lower-priority order can still fill leftover space")
        void lighterOrderFillsGap() {
            List<Order> orders = List.of(
                    order("H", CONNAUGHT_PLACE, 70, Priority.HIGH),
                    order("M", CONNAUGHT_PLACE, 50, Priority.MEDIUM),
                    order("L", CONNAUGHT_PLACE, 30, Priority.LOW));
            DispatchResult result = strategy.dispatch(orders, List.of(vehicle("V1", 100, CONNAUGHT_PLACE)));

            assertThat(orderIds(result.assignments().get(0))).containsExactly("H", "L");
            assertThat(result.unassigned()).extracting(u -> u.order().getOrderId()).containsExactly("M");
        }
    }

    @Nested
    @DisplayName("distance")
    class Distance {

        @Test
        @DisplayName("the nearest vehicle with capacity is chosen")
        void nearestVehicleChosen() {
            List<Order> orders = List.of(order("O1", NOIDA_SECTOR_18, 10, Priority.HIGH));
            List<Vehicle> vehicles = List.of(
                    vehicle("V-GURGAON", 100, CYBER_HUB_GURGAON),
                    vehicle("V-NOIDA", 100, NOIDA_SECTOR_18),
                    vehicle("V-KAROL", 100, KAROL_BAGH));

            Map<String, VehicleAssignment> plan = byVehicleId(strategy.dispatch(orders, vehicles));

            assertThat(orderIds(plan.get("V-NOIDA"))).containsExactly("O1");
            assertThat(plan.get("V-NOIDA").totalDistanceKm()).isZero();
        }

        @Test
        @DisplayName("ties on distance go to the lowest vehicleId")
        void tieBreakByVehicleId() {
            List<Order> orders = List.of(order("O1", CONNAUGHT_PLACE, 10, Priority.HIGH));
            List<Vehicle> vehicles = List.of(vehicle("V2", 100, KAROL_BAGH), vehicle("V1", 100, KAROL_BAGH));

            Map<String, VehicleAssignment> plan = byVehicleId(strategy.dispatch(orders, vehicles));

            assertThat(orderIds(plan.get("V1"))).containsExactly("O1");
            assertThat(plan.get("V2").orders()).isEmpty();
        }

        @Test
        @DisplayName("total distance is the sum of legs start -> stop1 -> stop2, and the vehicle moves with each drop")
        void distanceAccumulatesLegs() {
            List<Order> orders = List.of(
                    order("O1", CONNAUGHT_PLACE, 20, Priority.HIGH),
                    order("O2", NOIDA_SECTOR_18, 10, Priority.HIGH));
            DispatchResult result = strategy.dispatch(orders, List.of(vehicle("V1", 100, KAROL_BAGH)));

            double expected = distance.distanceKm(KAROL_BAGH[0], KAROL_BAGH[1], CONNAUGHT_PLACE[0], CONNAUGHT_PLACE[1])
                    + distance.distanceKm(CONNAUGHT_PLACE[0], CONNAUGHT_PLACE[1], NOIDA_SECTOR_18[0], NOIDA_SECTOR_18[1]);
            VehicleAssignment v1 = result.assignments().get(0);
            assertThat(orderIds(v1)).containsExactly("O1", "O2");
            assertThat(v1.totalDistanceKm()).isCloseTo(expected, within(1e-9));
        }

        @Test
        @DisplayName("a vehicle that has moved towards an area is preferred for later orders there")
        void movedVehicleIsPreferred() {
            // On the equator: V1 starts at lon 0, V2 at lon 1 but is too small for O1.
            // V1 delivers O1 at lon 0.90 and is then 0.02° from O2 (lon 0.92), closer than V2 (0.08°),
            // although from their starting points V2 would have been nearer to O2.
            List<Order> orders = List.of(
                    order("O1", new double[]{0, 0.90}, 20, Priority.HIGH),
                    order("O2", new double[]{0, 0.92}, 10, Priority.MEDIUM));
            List<Vehicle> vehicles = List.of(
                    vehicle("V1", 100, new double[]{0, 0}),
                    vehicle("V2", 15, new double[]{0, 1}));

            Map<String, VehicleAssignment> plan = byVehicleId(strategy.dispatch(orders, vehicles));

            assertThat(orderIds(plan.get("V1"))).containsExactly("O1", "O2");
            assertThat(plan.get("V2").orders()).isEmpty();
        }
    }

    @Nested
    @DisplayName("edge cases")
    class EdgeCases {

        @Test
        @DisplayName("no orders: every vehicle is listed idle with zero load and distance")
        void emptyOrders() {
            DispatchResult result = strategy.dispatch(List.of(),
                    List.of(vehicle("V2", 50, KAROL_BAGH), vehicle("V1", 50, KAROL_BAGH)));

            assertThat(result.unassigned()).isEmpty();
            assertThat(result.assignments()).extracting(a -> a.vehicle().getVehicleId()).containsExactly("V1", "V2");
            assertThat(result.assignments()).allSatisfy(a -> {
                assertThat(a.orders()).isEmpty();
                assertThat(a.totalLoad()).isZero();
                assertThat(a.totalDistanceKm()).isZero();
            });
        }

        @Test
        @DisplayName("no vehicles: every order is unassigned")
        void emptyVehicles() {
            List<Order> orders = List.of(
                    order("O1", CONNAUGHT_PLACE, 10, Priority.HIGH),
                    order("O2", CONNAUGHT_PLACE, 10, Priority.LOW));

            DispatchResult result = strategy.dispatch(orders, List.of());

            assertThat(result.assignments()).isEmpty();
            assertThat(result.unassigned())
                    .extracting(UnassignedOrder::reason)
                    .containsOnly(GreedyNearestVehicleStrategy.REASON_NO_VEHICLES)
                    .hasSize(2);
        }

        @Test
        @DisplayName("no orders and no vehicles yields an empty result")
        void emptyEverything() {
            DispatchResult result = strategy.dispatch(List.of(), List.of());

            assertThat(result.assignments()).isEmpty();
            assertThat(result.unassigned()).isEmpty();
        }

        @Test
        @DisplayName("input lists are not modified")
        void inputsUntouched() {
            List<Order> orders = new ArrayList<>(List.of(
                    order("B", CONNAUGHT_PLACE, 1, Priority.LOW),
                    order("A", CONNAUGHT_PLACE, 1, Priority.HIGH)));
            strategy.dispatch(orders, List.of(vehicle("V1", 10, CONNAUGHT_PLACE)));

            assertThat(orders).extracting(Order::getOrderId).containsExactly("B", "A");
        }
    }
}
