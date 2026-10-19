package com.dispatch.service;

import com.dispatch.dto.DispatchPlanResponse;

/**
 * Builds dispatch plans from the stored orders and vehicles.
 */
public interface DispatchService {

    /**
     * Computes the plan for all current orders and vehicles. Never throws for empty data:
     * missing orders or vehicles yield an empty plan with an explanatory message.
     */
    DispatchPlanResponse generatePlan();
}
