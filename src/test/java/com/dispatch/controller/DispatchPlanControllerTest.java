package com.dispatch.controller;

import com.dispatch.dto.DispatchPlanResponse;
import com.dispatch.dto.OrderResponse;
import com.dispatch.dto.UnassignedOrderResponse;
import com.dispatch.dto.VehiclePlanResponse;
import com.dispatch.entity.Priority;
import com.dispatch.service.DispatchService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DispatchPlanController.class)
class DispatchPlanControllerTest {

    private static final String URL = "/api/dispatch/plan";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DispatchService dispatchService;

    @Test
    @DisplayName("returns the plan in the spec's JSON shape, plus unassignedOrders")
    void returnsPlan() throws Exception {
        OrderResponse order = new OrderResponse("ORD001", 12.9716, 77.5946,
                "MG Road, Bangalore, Karnataka, India", 10, Priority.HIGH);
        when(dispatchService.generatePlan()).thenReturn(new DispatchPlanResponse(
                List.of(new VehiclePlanResponse("VEH001", 10, "5.07 km", List.of(order))),
                List.of(new UnassignedOrderResponse("ORD999", "No vehicles available")),
                null));

        mockMvc.perform(get(URL))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {
                          "dispatchPlan": [
                            {
                              "vehicleId": "VEH001",
                              "totalLoad": 10,
                              "totalDistance": "5.07 km",
                              "assignedOrders": [
                                {
                                  "orderId": "ORD001",
                                  "latitude": 12.9716,
                                  "longitude": 77.5946,
                                  "address": "MG Road, Bangalore, Karnataka, India",
                                  "packageWeight": 10,
                                  "priority": "HIGH"
                                }
                              ]
                            }
                          ],
                          "unassignedOrders": [
                            { "orderId": "ORD999", "reason": "No vehicles available" }
                          ]
                        }""", true))
                // Whole numbers are written as integers, as in the specification
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"totalLoad\":10,")));
    }

    @Test
    @DisplayName("an empty plan is still 200 and carries an explanatory message")
    void emptyPlanHasMessage() throws Exception {
        when(dispatchService.generatePlan())
                .thenReturn(new DispatchPlanResponse(List.of(), List.of(), "No orders available to dispatch."));

        mockMvc.perform(get(URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dispatchPlan").isEmpty())
                .andExpect(jsonPath("$.unassignedOrders").isEmpty())
                .andExpect(jsonPath("$.message").value("No orders available to dispatch."));
    }

    @Test
    @DisplayName("POST to the plan endpoint returns 405")
    void postNotAllowed() throws Exception {
        mockMvc.perform(post(URL))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    @DisplayName("an unknown path returns 404 in the standard error format")
    void unknownPathReturns404() throws Exception {
        mockMvc.perform(get("/api/dispatch/unknown"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message").value("Resource not found: /api/dispatch/unknown"));
    }
}
