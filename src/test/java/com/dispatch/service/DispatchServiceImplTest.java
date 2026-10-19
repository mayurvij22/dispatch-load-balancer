package com.dispatch.service;

import com.dispatch.dto.DispatchPlanResponse;
import com.dispatch.entity.Order;
import com.dispatch.entity.Priority;
import com.dispatch.entity.Vehicle;
import com.dispatch.mapper.DispatchPlanMapper;
import com.dispatch.mapper.OrderMapper;
import com.dispatch.repository.OrderRepository;
import com.dispatch.repository.VehicleRepository;
import com.dispatch.service.impl.DispatchServiceImpl;
import com.dispatch.strategy.DispatchResult;
import com.dispatch.strategy.DispatchStrategy;
import com.dispatch.strategy.UnassignedOrder;
import com.dispatch.strategy.VehicleAssignment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static com.dispatch.TestFixtures.CONNAUGHT_PLACE;
import static com.dispatch.TestFixtures.KAROL_BAGH;
import static com.dispatch.TestFixtures.order;
import static com.dispatch.TestFixtures.vehicle;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DispatchServiceImplTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private VehicleRepository vehicleRepository;
    @Mock
    private DispatchStrategy dispatchStrategy;

    private DispatchService dispatchService;

    private final Order order = order("ORD001", CONNAUGHT_PLACE, 12.5, Priority.HIGH);
    private final Vehicle vehicle = vehicle("VEH001", 100, KAROL_BAGH);

    @BeforeEach
    void setUp() {
        dispatchService = new DispatchServiceImpl(orderRepository, vehicleRepository, dispatchStrategy,
                new DispatchPlanMapper(new OrderMapper()));
    }

    @Test
    @DisplayName("loads all data once, delegates to the strategy and maps the result")
    void generatesPlan() {
        when(orderRepository.findAll()).thenReturn(List.of(order));
        when(vehicleRepository.findAll()).thenReturn(List.of(vehicle));
        when(dispatchStrategy.dispatch(List.of(order), List.of(vehicle))).thenReturn(new DispatchResult(
                List.of(new VehicleAssignment(vehicle, List.of(order), 12.5, 14.3129)), List.of()));

        DispatchPlanResponse plan = dispatchService.generatePlan();

        verify(dispatchStrategy).dispatch(List.of(order), List.of(vehicle));
        assertThat(plan.message()).isNull();
        assertThat(plan.unassignedOrders()).isEmpty();
        assertThat(plan.dispatchPlan()).singleElement().satisfies(v -> {
            assertThat(v.vehicleId()).isEqualTo("VEH001");
            assertThat(v.totalLoad()).isEqualTo(12.5);
            assertThat(v.totalDistance()).isEqualTo("14.31 km");
            assertThat(v.assignedOrders()).singleElement().satisfies(o -> {
                assertThat(o.orderId()).isEqualTo("ORD001");
                assertThat(o.address()).isEqualTo("Address of ORD001");
                assertThat(o.priority()).isEqualTo(Priority.HIGH);
            });
        });
    }

    @Test
    @DisplayName("unassigned orders are reported with a summary message")
    void reportsUnassignedOrders() {
        when(orderRepository.findAll()).thenReturn(List.of(order));
        when(vehicleRepository.findAll()).thenReturn(List.of(vehicle));
        when(dispatchStrategy.dispatch(List.of(order), List.of(vehicle))).thenReturn(new DispatchResult(
                List.of(new VehicleAssignment(vehicle, List.of(), 0, 0)),
                List.of(new UnassignedOrder(order, "too heavy"))));

        DispatchPlanResponse plan = dispatchService.generatePlan();

        assertThat(plan.message()).isEqualTo("1 order(s) could not be assigned. See unassignedOrders for details.");
        assertThat(plan.unassignedOrders()).singleElement().satisfies(u -> {
            assertThat(u.orderId()).isEqualTo("ORD001");
            assertThat(u.reason()).isEqualTo("too heavy");
        });
        assertThat(plan.dispatchPlan()).singleElement().satisfies(v -> {
            assertThat(v.totalLoad()).isZero();
            assertThat(v.totalDistance()).isEqualTo("0.00 km");
        });
    }

    @Test
    @DisplayName("no orders and no vehicles returns an empty plan with an explanatory message")
    void noData() {
        stubEmptyStrategy(List.of(), List.of());

        DispatchPlanResponse plan = dispatchService.generatePlan();

        assertThat(plan.dispatchPlan()).isEmpty();
        assertThat(plan.unassignedOrders()).isEmpty();
        assertThat(plan.message()).startsWith("No orders or vehicles available");
    }

    @Test
    @DisplayName("no orders returns idle vehicles with a message")
    void noOrders() {
        stubEmptyStrategy(List.of(), List.of(vehicle));

        assertThat(dispatchService.generatePlan().message()).isEqualTo("No orders available to dispatch.");
    }

    @Test
    @DisplayName("no vehicles returns a message saying all orders are unassigned")
    void noVehicles() {
        stubEmptyStrategy(List.of(order), List.of());

        assertThat(dispatchService.generatePlan().message())
                .isEqualTo("No vehicles available. All orders are unassigned.");
    }

    private void stubEmptyStrategy(List<Order> orders, List<Vehicle> vehicles) {
        when(orderRepository.findAll()).thenReturn(orders);
        when(vehicleRepository.findAll()).thenReturn(vehicles);
        when(dispatchStrategy.dispatch(orders, vehicles)).thenReturn(new DispatchResult(List.of(), List.of()));
    }
}
