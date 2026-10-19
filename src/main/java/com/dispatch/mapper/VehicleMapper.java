package com.dispatch.mapper;

import com.dispatch.dto.VehicleRequest;
import com.dispatch.entity.Vehicle;
import org.springframework.stereotype.Component;

/**
 * Converts vehicle DTOs to the {@link Vehicle} entity.
 */
@Component
public class VehicleMapper {

    public Vehicle toEntity(VehicleRequest request) {
        return new Vehicle(request.vehicleId(), request.capacity(), request.currentLatitude(),
                request.currentLongitude(), request.currentAddress());
    }

    /** Copies the request onto an already-persisted entity (upsert path). */
    public void updateEntity(Vehicle vehicle, VehicleRequest request) {
        vehicle.update(request.capacity(), request.currentLatitude(), request.currentLongitude(),
                request.currentAddress());
    }
}
