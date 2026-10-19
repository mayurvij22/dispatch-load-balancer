package com.dispatch.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * A single vehicle in a {@link VehiclesRequest}.
 */
@Schema(description = "A delivery vehicle")
public record VehicleRequest(

        @Schema(example = "VEH001")
        @NotBlank(message = "vehicleId is required")
        @Size(max = 64, message = "vehicleId must be at most 64 characters")
        String vehicleId,

        @Schema(example = "100")
        @NotNull(message = "capacity is required")
        @Positive(message = "capacity must be greater than 0")
        Double capacity,

        @Schema(example = "28.7041")
        @NotNull(message = "currentLatitude is required")
        @DecimalMin(value = "-90.0", message = "currentLatitude must be between -90 and 90")
        @DecimalMax(value = "90.0", message = "currentLatitude must be between -90 and 90")
        Double currentLatitude,

        @Schema(example = "77.1025")
        @NotNull(message = "currentLongitude is required")
        @DecimalMin(value = "-180.0", message = "currentLongitude must be between -180 and 180")
        @DecimalMax(value = "180.0", message = "currentLongitude must be between -180 and 180")
        Double currentLongitude,

        @Schema(example = "Karol Bagh, Delhi, India")
        @NotBlank(message = "currentAddress is required")
        @Size(max = 255, message = "currentAddress must be at most 255 characters")
        String currentAddress
) {
}
