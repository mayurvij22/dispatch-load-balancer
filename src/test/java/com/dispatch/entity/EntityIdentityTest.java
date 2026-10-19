package com.dispatch.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EntityIdentityTest {

    @Test
    @DisplayName("orders are equal by orderId only")
    void orderIdentity() {
        Order a = new Order("ORD001", 1, 2, "A", 10, Priority.HIGH);
        Order sameId = new Order("ORD001", 3, 4, "B", 20, Priority.LOW);
        Order other = new Order("ORD002", 1, 2, "A", 10, Priority.HIGH);

        assertThat(a).isEqualTo(a).isEqualTo(sameId).hasSameHashCodeAs(sameId).isNotEqualTo(other).isNotEqualTo("ORD001");
        assertThat(a.getId()).isEqualTo("ORD001");
        assertThat(a.isNew()).isTrue();
        assertThat(a).hasToString("Order{orderId='ORD001', priority=HIGH, packageWeight=10.0}");
    }

    @Test
    @DisplayName("vehicles are equal by vehicleId only")
    void vehicleIdentity() {
        Vehicle a = new Vehicle("VEH001", 100, 1, 2, "A");
        Vehicle sameId = new Vehicle("VEH001", 50, 3, 4, "B");
        Vehicle other = new Vehicle("VEH002", 100, 1, 2, "A");

        assertThat(a).isEqualTo(a).isEqualTo(sameId).hasSameHashCodeAs(sameId).isNotEqualTo(other).isNotEqualTo("VEH001");
        assertThat(a.getId()).isEqualTo("VEH001");
        assertThat(a).hasToString("Vehicle{vehicleId='VEH001', capacity=100.0}");
    }

    @Test
    @DisplayName("loaded or persisted entities are no longer new")
    void lifecycleCallbacksClearNewFlag() {
        Order order = new Order("ORD001", 1, 2, "A", 10, Priority.HIGH);
        Vehicle vehicle = new Vehicle("VEH001", 100, 1, 2, "A");

        order.markNotNew();
        vehicle.markNotNew();

        assertThat(order.isNew()).isFalse();
        assertThat(vehicle.isNew()).isFalse();
    }
}
