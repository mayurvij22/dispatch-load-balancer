package com.dispatch.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WholeNumberDoubleModuleTest {

    private final ObjectMapper mapper = new ObjectMapper().registerModule(new WholeNumberDoubleModule());

    record Sample(double primitive, Double boxed) {
    }

    @Test
    @DisplayName("whole-number doubles serialise without a trailing .0")
    void wholeNumbersAsIntegers() throws Exception {
        assertThat(mapper.writeValueAsString(new Sample(10.0, 25.0))).isEqualTo("{\"primitive\":10,\"boxed\":25}");
    }

    @Test
    @DisplayName("fractional and out-of-range doubles are unchanged")
    void fractionalUnchanged() throws Exception {
        assertThat(mapper.writeValueAsString(new Sample(28.6139, -0.5)))
                .isEqualTo("{\"primitive\":28.6139,\"boxed\":-0.5}");
        assertThat(mapper.writeValueAsString(new Sample(1e300, Double.NaN)))
                .isEqualTo("{\"primitive\":1.0E300,\"boxed\":\"NaN\"}");
    }
}
