package com.dispatch.controller;

import com.dispatch.dto.ApiResponse;
import com.dispatch.dto.ErrorResponse;
import com.dispatch.dto.VehiclesRequest;
import com.dispatch.service.VehicleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/dispatch/vehicles", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Vehicles", description = "Submit and reset fleet details")
public class VehicleController {

    static final String VEHICLES_ACCEPTED = "Vehicle details accepted.";

    private final VehicleService vehicleService;

    public VehicleController(VehicleService vehicleService) {
        this.vehicleService = vehicleService;
    }

    @Operation(summary = "Submit fleet details",
            description = "Creates new vehicles and updates existing ones with the same vehicleId (upsert).")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Vehicles accepted"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                    description = "Validation failure, malformed JSON or duplicate vehicleId",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse> submitVehicles(@Valid @RequestBody VehiclesRequest request) {
        vehicleService.upsertVehicles(request.vehicles());
        return ResponseEntity.ok(ApiResponse.success(VEHICLES_ACCEPTED));
    }

    @Operation(summary = "Delete all vehicles")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "All vehicles deleted")
    @DeleteMapping
    public ResponseEntity<Void> deleteAllVehicles() {
        vehicleService.deleteAllVehicles();
        return ResponseEntity.noContent().build();
    }
}
