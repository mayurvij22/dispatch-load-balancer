package com.dispatch.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import org.springframework.data.domain.Persistable;

import java.util.Objects;

/**
 * A delivery vehicle with its load capacity and current position.
 * <p>
 * See {@link Order} for why {@link Persistable} is implemented.
 */
@Entity
@Table(name = "vehicles")
public class Vehicle implements Persistable<String> {

    @Id
    @Column(name = "vehicle_id", length = 64)
    private String vehicleId;

    @Column(nullable = false)
    private double capacity;

    @Column(name = "current_latitude", nullable = false)
    private double currentLatitude;

    @Column(name = "current_longitude", nullable = false)
    private double currentLongitude;

    @Column(name = "current_address", nullable = false, length = 255)
    private String currentAddress;

    @Transient
    private boolean isNew = true;

    protected Vehicle() {
        // required by JPA
    }

    public Vehicle(String vehicleId, double capacity, double currentLatitude, double currentLongitude,
                   String currentAddress) {
        this.vehicleId = vehicleId;
        update(capacity, currentLatitude, currentLongitude, currentAddress);
    }

    /**
     * Overwrites all mutable attributes; used when an existing vehicle is re-posted (upsert).
     */
    public final void update(double capacity, double currentLatitude, double currentLongitude,
                             String currentAddress) {
        this.capacity = capacity;
        this.currentLatitude = currentLatitude;
        this.currentLongitude = currentLongitude;
        this.currentAddress = currentAddress;
    }

    @PostLoad
    @PostPersist
    void markNotNew() {
        this.isNew = false;
    }

    @Override
    public String getId() {
        return vehicleId;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    public String getVehicleId() {
        return vehicleId;
    }

    public double getCapacity() {
        return capacity;
    }

    public double getCurrentLatitude() {
        return currentLatitude;
    }

    public double getCurrentLongitude() {
        return currentLongitude;
    }

    public String getCurrentAddress() {
        return currentAddress;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        return o instanceof Vehicle other && Objects.equals(vehicleId, other.vehicleId);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(vehicleId);
    }

    @Override
    public String toString() {
        return "Vehicle{vehicleId='%s', capacity=%s}".formatted(vehicleId, capacity);
    }
}
