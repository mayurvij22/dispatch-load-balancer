package com.dispatch.service;

import com.dispatch.dto.VehicleRequest;

import java.util.List;

/**
 * Manages the vehicle fleet.
 */
public interface VehicleService {

    /**
     * Inserts new vehicles and updates existing ones (matched by vehicleId) in one transaction.
     *
     * @return the number of vehicles saved
     * @throws com.dispatch.exception.DuplicateIdException if a vehicleId repeats within the request
     */
    int upsertVehicles(List<VehicleRequest> vehicles);

    /** Removes all vehicles. */
    void deleteAllVehicles();
}
