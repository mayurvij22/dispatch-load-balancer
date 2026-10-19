package com.dispatch.integration;

import com.dispatch.TestFixtures;
import com.dispatch.repository.OrderRepository;
import com.dispatch.repository.VehicleRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end flow against the real Spring context and the H2 database.
 */
@SpringBootTest
@AutoConfigureMockMvc
class DispatchFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private VehicleRepository vehicleRepository;

    @BeforeEach
    void resetData() throws Exception {
        mockMvc.perform(delete("/api/dispatch/orders")).andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/dispatch/vehicles")).andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("POST sample orders -> POST sample vehicles -> GET plan")
    void fullFlowWithSampleData() throws Exception {
        postJson("/api/dispatch/orders", TestFixtures.readResource(TestFixtures.SAMPLE_ORDERS_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Delivery orders accepted."))
                .andExpect(jsonPath("$.status").value("success"));
        postJson("/api/dispatch/vehicles", TestFixtures.readResource(TestFixtures.SAMPLE_VEHICLES_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Vehicle details accepted."));

        String json = mockMvc.perform(get("/api/dispatch/plan"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dispatchPlan", hasSize(5)))
                .andReturn().getResponse().getContentAsString();

        JsonNode plan = objectMapper.readTree(json);
        Set<String> seenOrders = new HashSet<>();
        double assignedWeight = 0;
        for (JsonNode vehicle : plan.get("dispatchPlan")) {
            double load = 0;
            for (JsonNode order : vehicle.get("assignedOrders")) {
                assertThat(seenOrders.add(order.get("orderId").asText())).isTrue();
                assertThat(order.get("address").asText()).isNotBlank();
                load += order.get("packageWeight").asDouble();
            }
            assertThat(vehicle.get("totalLoad").asDouble()).isEqualTo(load);
            assertThat(vehicle.get("totalDistance").asText()).matches("\\d+\\.\\d{2} km");
            assignedWeight += load;
        }
        for (JsonNode unassigned : plan.get("unassignedOrders")) {
            assertThat(seenOrders.add(unassigned.get("orderId").asText())).isTrue();
            assertThat(unassigned.get("reason").asText()).isNotBlank();
        }
        assertThat(seenOrders).hasSize(30);
        assertThat(assignedWeight).isLessThanOrEqualTo(500);
        assertThat(plan.get("message").asText()).contains("could not be assigned");
    }

    @Test
    @DisplayName("re-posting an existing ID updates the row instead of duplicating it")
    void repostUpserts() throws Exception {
        postJson("/api/dispatch/orders", TestFixtures.readResource(TestFixtures.SAMPLE_ORDERS_JSON))
                .andExpect(status().isOk());
        postJson("/api/dispatch/orders", """
                {"orders": [{"orderId": "ORD001", "latitude": 28.6139, "longitude": 77.2090,
                  "address": "Updated address", "packageWeight": 99, "priority": "LOW"}]}""")
                .andExpect(status().isOk());

        assertThat(orderRepository.count()).isEqualTo(30);
        assertThat(orderRepository.findById("ORD001")).hasValueSatisfying(order -> {
            assertThat(order.getAddress()).isEqualTo("Updated address");
            assertThat(order.getPackageWeight()).isEqualTo(99);
        });
    }

    @Test
    @DisplayName("duplicate IDs in one request are rejected and nothing is saved")
    void duplicateIdsRejected() throws Exception {
        postJson("/api/dispatch/vehicles", """
                {"vehicles": [
                  {"vehicleId": "V1", "capacity": 10, "currentLatitude": 0, "currentLongitude": 0, "currentAddress": "A"},
                  {"vehicleId": "V1", "capacity": 20, "currentLatitude": 0, "currentLongitude": 0, "currentAddress": "B"}
                ]}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Duplicate IDs in request"));

        assertThat(vehicleRepository.count()).isZero();
    }

    @Test
    @DisplayName("GET plan with no data returns 200 with an empty plan and a message")
    void emptyPlan() throws Exception {
        mockMvc.perform(get("/api/dispatch/plan"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dispatchPlan").isEmpty())
                .andExpect(jsonPath("$.unassignedOrders").isEmpty())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("orders without vehicles are all reported as unassigned")
    void ordersWithoutVehicles() throws Exception {
        postJson("/api/dispatch/orders", TestFixtures.readResource(TestFixtures.SAMPLE_ORDERS_JSON))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/dispatch/plan"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dispatchPlan").isEmpty())
                .andExpect(jsonPath("$.unassignedOrders", hasSize(30)))
                .andExpect(jsonPath("$.unassignedOrders[0].reason").value("No vehicles available"));
    }

    @Test
    @DisplayName("Swagger UI and OpenAPI docs are served")
    void openApiDocsAvailable() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("Dispatch Load Balancer API"))
                .andExpect(jsonPath("$.paths['/api/dispatch/plan']").exists());
    }

    @Test
    @DisplayName("the health endpoint used by the Render health check reports UP")
    void healthEndpointUp() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    private org.springframework.test.web.servlet.ResultActions postJson(String url, String body) throws Exception {
        return mockMvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(body));
    }
}
