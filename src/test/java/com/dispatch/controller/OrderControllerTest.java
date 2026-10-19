package com.dispatch.controller;

import com.dispatch.TestFixtures;
import com.dispatch.exception.DuplicateIdException;
import com.dispatch.service.OrderService;
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

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrderController.class)
class OrderControllerTest {

    private static final String URL = "/api/dispatch/orders";

    private static final String VALID_ORDER = """
            {"orderId": "ORD001", "latitude": 12.9716, "longitude": 77.5946,
             "address": "MG Road, Bangalore, Karnataka, India", "packageWeight": 10, "priority": "HIGH"}""";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderService orderService;

    private static String body(String... orders) {
        return "{\"orders\": [" + String.join(",", orders) + "]}";
    }

    /** Returns VALID_ORDER with one field's value (a quoted string or a bare literal) replaced. */
    private static String orderWith(String field, String jsonValue) {
        return VALID_ORDER.replaceFirst("\"" + field + "\": (\"[^\"]*\"|[^,}\\s]+)", "\"" + field + "\": " + jsonValue);
    }

    @Test
    @DisplayName("valid orders return 200 with the success message from the spec")
    void acceptsValidOrders() throws Exception {
        when(orderService.upsertOrders(anyList())).thenReturn(1);

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body(VALID_ORDER)))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {"message": "Delivery orders accepted.", "status": "success"}""", true));
        verify(orderService).upsertOrders(anyList());
    }

    @Test
    @DisplayName("the 30 sample orders from the PDF are accepted")
    void acceptsSampleOrders() throws Exception {
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
                        .content(TestFixtures.readResource(TestFixtures.SAMPLE_ORDERS_JSON)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("lower-case priority is accepted")
    void acceptsLowerCasePriority() throws Exception {
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
                        .content(body(orderWith("priority", "\"medium\""))))
                .andExpect(status().isOk());
    }

    @ParameterizedTest(name = "{0} = {1} -> {2}")
    @CsvSource(delimiter = '|', value = {
            "latitude      | 90.5     | latitude must be between -90 and 90",
            "latitude      | -91      | latitude must be between -90 and 90",
            "longitude     | 180.1    | longitude must be between -180 and 180",
            "longitude     | null     | longitude is required",
            "packageWeight | 0        | packageWeight must be greater than 0",
            "packageWeight | -5       | packageWeight must be greater than 0",
            "orderId       | \"  \"   | orderId is required",
            "address       | \"\"     | address is required",
            "priority      | null     | priority is required (HIGH, MEDIUM or LOW)"
    })
    @DisplayName("invalid fields return 400 with a field-level error")
    void rejectsInvalidField(String field, String jsonValue, String expectedMessage) throws Exception {
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
                        .content(body(VALID_ORDER, orderWith(field, jsonValue))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.errors", hasSize(1)))
                .andExpect(jsonPath("$.errors[0].field").value("orders[1]." + field))
                .andExpect(jsonPath("$.errors[0].message").value(expectedMessage))
                .andExpect(jsonPath("$.timestamp", notNullValue()));
        verifyNoInteractions(orderService);
    }

    @Test
    @DisplayName("multiple missing fields are all reported")
    void reportsAllMissingFields() throws Exception {
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body("{}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasSize(6)));
    }

    @Test
    @DisplayName("an empty orders list returns 400")
    void rejectsEmptyList() throws Exception {
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("{\"orders\": []}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("orders"))
                .andExpect(jsonPath("$.errors[0].message").value("orders must contain at least one order"));
    }

    @Test
    @DisplayName("a missing orders field returns 400")
    void rejectsMissingList() throws Exception {
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("orders"));
    }

    @Test
    @DisplayName("a null element in the list returns 400")
    void rejectsNullElement() throws Exception {
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body(VALID_ORDER, "null")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("orders[1]"));
    }

    @Test
    @DisplayName("an unknown priority returns 400 listing the allowed values")
    void rejectsInvalidPriority() throws Exception {
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
                        .content(body(VALID_ORDER, orderWith("priority", "\"URGENT\""))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message").value("Invalid value in request body"))
                .andExpect(jsonPath("$.errors[0].field").value("orders[1].priority"))
                .andExpect(jsonPath("$.errors[0].message")
                        .value("Invalid value 'URGENT'. Allowed values: [HIGH, MEDIUM, LOW]"));
    }

    @Test
    @DisplayName("a non-numeric latitude returns 400 naming the field")
    void rejectsNonNumericLatitude() throws Exception {
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
                        .content(body(orderWith("latitude", "\"north\""))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("orders[0].latitude"))
                .andExpect(jsonPath("$.errors[0].message").value("Invalid value 'north'; expected Double"));
    }

    @Test
    @DisplayName("orders given as an object instead of a list returns 400")
    void rejectsWrongStructure() throws Exception {
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("{\"orders\": " + VALID_ORDER + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("orders"));
    }

    @Test
    @DisplayName("malformed JSON returns 400 without parser details")
    void rejectsMalformedJson() throws Exception {
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("{\"orders\": [ {\"orderId\": "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Malformed JSON request"))
                .andExpect(jsonPath("$.errors", empty()));
    }

    @Test
    @DisplayName("an empty body returns 400")
    void rejectsEmptyBody() throws Exception {
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Malformed JSON request")));
    }

    @Test
    @DisplayName("duplicate orderIds return 400 listing each duplicate")
    void rejectsDuplicateIds() throws Exception {
        when(orderService.upsertOrders(anyList()))
                .thenThrow(new DuplicateIdException("orderId", List.of("ORD001")));

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body(VALID_ORDER, VALID_ORDER)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Duplicate IDs in request"))
                .andExpect(jsonPath("$.errors[0].field").value("orderId"))
                .andExpect(jsonPath("$.errors[0].message").value("'ORD001' appears more than once in the request"));
    }

    @Test
    @DisplayName("a non-JSON content type returns 415")
    void rejectsWrongContentType() throws Exception {
        mockMvc.perform(post(URL).contentType(MediaType.TEXT_PLAIN).content("hello"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.status").value("error"));
    }

    @Test
    @DisplayName("an unsupported method returns 405")
    void rejectsUnsupportedMethod() throws Exception {
        mockMvc.perform(get(URL))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.message").value("HTTP method GET is not supported for this endpoint"));
    }

    @Test
    @DisplayName("unexpected errors return 500 without leaking internals")
    void hidesUnexpectedErrors() throws Exception {
        when(orderService.upsertOrders(anyList())).thenThrow(new IllegalStateException("db password is hunter2"));

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body(VALID_ORDER)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message").value("An unexpected error occurred. Please try again later."))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("hunter2"))));
    }

    @Test
    @DisplayName("DELETE removes all orders and returns 204")
    void deletesAllOrders() throws Exception {
        mockMvc.perform(delete(URL))
                .andExpect(status().isNoContent());
        verify(orderService).deleteAllOrders();
    }
}
