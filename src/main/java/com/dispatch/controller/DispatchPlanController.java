package com.dispatch.controller;

import com.dispatch.dto.DispatchPlanResponse;
import com.dispatch.service.DispatchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/dispatch/plan", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Dispatch plan", description = "Compute the optimised dispatch plan")
public class DispatchPlanController {

    private final DispatchService dispatchService;

    public DispatchPlanController(DispatchService dispatchService) {
        this.dispatchService = dispatchService;
    }

    @Operation(summary = "Get the dispatch plan",
            description = "Assigns all stored orders to the stored vehicles. Always 200: when there are no orders "
                    + "or no vehicles the plan is empty and 'message' explains why.")
    @GetMapping
    public ResponseEntity<DispatchPlanResponse> getDispatchPlan() {
        return ResponseEntity.ok(dispatchService.generatePlan());
    }
}
