package com.dispatch.mapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class DispatchPlanMapperTest {

    @ParameterizedTest(name = "{0} km -> \"{1}\"")
    @CsvSource({
            "0,         0.00 km",
            "5,         5.00 km",
            "12.344,    12.34 km",
            "12.345678, 12.35 km",
            "1234.5,    1234.50 km"
    })
    @DisplayName("distances are formatted with two decimals and a km suffix")
    void formatsDistance(double km, String expected) {
        assertThat(DispatchPlanMapper.formatDistance(km)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "0.30000000000000004, 0.3",
            "100,                 100",
            "12.346,              12.35",
            "99.994,              99.99"
    })
    @DisplayName("loads are rounded to two decimals to hide floating-point noise")
    void roundsLoad(double raw, double expected) {
        assertThat(DispatchPlanMapper.roundToTwoDecimals(raw)).isEqualTo(expected);
    }
}
