package com.dispatch.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Request body of {@code POST /api/dispatch/vehicles}.
 */
public record VehiclesRequest(
        @NotEmpty(message = "vehicles must contain at least one vehicle")
        List<@NotNull(message = "vehicle must not be null") @Valid VehicleRequest> vehicles
) {
}
