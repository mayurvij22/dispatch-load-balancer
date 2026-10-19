package com.dispatch.controller;

import com.dispatch.TestFixtures;
import com.dispatch.exception.DuplicateIdException;
import com.dispatch.service.VehicleService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(VehicleController.class)
class VehicleControllerTest {

    private static final String URL = "/api/dispatch/vehicles";

    private static final String VALID_VEHICLE = """
            {"vehicleId": "VEH001", "capacity": 100, "currentLatitude": 12.9716, "currentLongitude": 77.6413,
             "currentAddress": "Indiranagar, Bangalore, Karnataka, India"}""";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private VehicleService vehicleService;

    private static String body(String... vehicles) {
        return "{\"vehicles\": [" + String.join(",", vehicles) + "]}";
    }

    /** Returns VALID_VEHICLE with one field's value (a quoted string or a bare literal) replaced. */
    private static String vehicleWith(String field, String jsonValue) {
        return VALID_VEHICLE.replaceFirst("\"" + field + "\": (\"[^\"]*\"|[^,}\\s]+)", "\"" + field + "\": " + jsonValue);
    }

    @Test
    @DisplayName("valid vehicles return 200 with the success message from the spec")
    void acceptsValidVehicles() throws Exception {
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body(VALID_VEHICLE)))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {"message": "Vehicle details accepted.", "status": "success"}""", true));
        verify(vehicleService).upsertVehicles(anyList());
    }

    @Test
    @DisplayName("the 5 sample vehicles from the PDF are accepted")
    void acceptsSampleVehicles() throws Exception {
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
                        .content(TestFixtures.readResource(TestFixtures.SAMPLE_VEHICLES_JSON)))
                .andExpect(status().isOk());
    }

    @ParameterizedTest(name = "{0} = {1} -> {2}")
    @CsvSource(delimiter = '|', value = {
            "capacity         | 0      | capacity must be greater than 0",
            "capacity         | -10    | capacity must be greater than 0",
            "capacity         | null   | capacity is required",
            "currentLatitude  | 100    | currentLatitude must be between -90 and 90",
            "currentLongitude | -200   | currentLongitude must be between -180 and 180",
            "vehicleId        | \"\"   | vehicleId is required",
            "currentAddress   | null   | currentAddress is required"
    })
    @DisplayName("invalid fields return 400 with a field-level error")
    void rejectsInvalidField(String field, String jsonValue, String expectedMessage) throws Exception {
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body(vehicleWith(field, jsonValue))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.errors", hasSize(1)))
                .andExpect(jsonPath("$.errors[0].field").value("vehicles[0]." + field))
                .andExpect(jsonPath("$.errors[0].message").value(expectedMessage));
        verifyNoInteractions(vehicleService);
    }

    @Test
    @DisplayName("an empty vehicles list returns 400")
    void rejectsEmptyList() throws Exception {
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("{\"vehicles\": []}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].message").value("vehicles must contain at least one vehicle"));
    }

    @Test
    @DisplayName("malformed JSON returns 400")
    void rejectsMalformedJson() throws Exception {
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("{vehicles: ["))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Malformed JSON request"));
    }

    @Test
    @DisplayName("duplicate vehicleIds return 400")
    void rejectsDuplicateIds() throws Exception {
        when(vehicleService.upsertVehicles(anyList()))
                .thenThrow(new DuplicateIdException("vehicleId", List.of("VEH001")));

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body(VALID_VEHICLE, VALID_VEHICLE)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("vehicleId"));
    }

    @Test
    @DisplayName("DELETE removes all vehicles and returns 204")
    void deletesAllVehicles() throws Exception {
        mockMvc.perform(delete(URL))
                .andExpect(status().isNoContent());
        verify(vehicleService).deleteAllVehicles();
    }
}
