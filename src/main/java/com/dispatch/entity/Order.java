package com.dispatch.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import org.springframework.data.domain.Persistable;

import java.util.Objects;

/**
 * A delivery order with its drop location, weight and priority.
 * <p>
 * Implements {@link Persistable} because the ID is client-assigned: without it Spring Data
 * would treat every entity as "existing" and issue a SELECT before each INSERT.
 * The service layer decides new vs. existing with a single bulk lookup instead.
 */
@Entity
@Table(name = "orders") // "order" is a reserved SQL keyword
public class Order implements Persistable<String> {

    @Id
    @Column(name = "order_id", length = 64)
    private String orderId;

    @Column(nullable = false)
    private double latitude;

    @Column(nullable = false)
    private double longitude;

    @Column(nullable = false, length = 255)
    private String address;

    @Column(name = "package_weight", nullable = false)
    private double packageWeight;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Priority priority;

    @Transient
    private boolean isNew = true;

    protected Order() {
        // required by JPA
    }

    public Order(String orderId, double latitude, double longitude, String address,
                 double packageWeight, Priority priority) {
        this.orderId = orderId;
        update(latitude, longitude, address, packageWeight, priority);
    }

    /**
     * Overwrites all mutable attributes; used when an existing order is re-posted (upsert).
     */
    public final void update(double latitude, double longitude, String address,
                             double packageWeight, Priority priority) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.address = address;
        this.packageWeight = packageWeight;
        this.priority = priority;
    }

    @PostLoad
    @PostPersist
    void markNotNew() {
        this.isNew = false;
    }

    @Override
    public String getId() {
        return orderId;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    public String getOrderId() {
        return orderId;
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public String getAddress() {
        return address;
    }

    public double getPackageWeight() {
        return packageWeight;
    }

    public Priority getPriority() {
        return priority;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        return o instanceof Order other && Objects.equals(orderId, other.orderId);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(orderId);
    }

    @Override
    public String toString() {
        return "Order{orderId='%s', priority=%s, packageWeight=%s}".formatted(orderId, priority, packageWeight);
    }
}
