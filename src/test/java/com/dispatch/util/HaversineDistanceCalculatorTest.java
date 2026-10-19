package com.dispatch.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class HaversineDistanceCalculatorTest {

    private final DistanceCalculator calculator = new HaversineDistanceCalculator();

    @Test
    @DisplayName("distance from a point to itself is zero")
    void samePointIsZero() {
        assertThat(calculator.distanceKm(28.6139, 77.2090, 28.6139, 77.2090)).isZero();
    }

    @ParameterizedTest(name = "{0} -> {5} km")
    @CsvSource({
            // name,                                lat1,    lon1,    lat2,    lon2,    expectedKm
            "'Connaught Place -> Noida Sector 18', 28.6139, 77.2090, 28.5355, 77.3910, 19.82",
            "'Connaught Place -> Karol Bagh',      28.6139, 77.2090, 28.7041, 77.1025, 14.31",
            "'Bangalore MG Road -> Chennai',       12.9716, 77.5946, 13.0827, 80.2707, 290.17",
            "'London -> Paris',                    51.5074, -0.1278, 48.8566,  2.3522, 343.56"
    })
    @DisplayName("known city pairs are within 1 km of reference values")
    void knownCityPairs(String name, double lat1, double lon1, double lat2, double lon2, double expectedKm) {
        assertThat(calculator.distanceKm(lat1, lon1, lat2, lon2)).isCloseTo(expectedKm, within(1.0));
    }

    @Test
    @DisplayName("distance is symmetric")
    void isSymmetric() {
        double ab = calculator.distanceKm(12.9716, 77.5946, 13.0827, 80.2707);
        double ba = calculator.distanceKm(13.0827, 80.2707, 12.9716, 77.5946);
        assertThat(ab).isCloseTo(ba, within(1e-9));
    }

    @Test
    @DisplayName("pole to pole is half the Earth's circumference")
    void poleToPole() {
        double halfCircumference = Math.PI * HaversineDistanceCalculator.EARTH_RADIUS_KM;
        assertThat(calculator.distanceKm(90, 0, -90, 0)).isCloseTo(halfCircumference, within(1e-6));
    }

    @Test
    @DisplayName("all longitudes meet at the pole")
    void longitudeIrrelevantAtPole() {
        assertThat(calculator.distanceKm(90, 0, 90, 135)).isCloseTo(0.0, within(1e-6));
    }

    @Test
    @DisplayName("crossing the antimeridian takes the short way round")
    void antimeridianCrossing() {
        // 179.5°E to 179.5°W on the equator is 1° of arc (~111.19 km), not 359°
        double oneDegreeKm = Math.toRadians(1) * HaversineDistanceCalculator.EARTH_RADIUS_KM;
        assertThat(calculator.distanceKm(0, 179.5, 0, -179.5)).isCloseTo(oneDegreeKm, within(1e-6));
    }

    @Test
    @DisplayName("antipodal points do not produce NaN")
    void antipodalPoints() {
        double halfCircumference = Math.PI * HaversineDistanceCalculator.EARTH_RADIUS_KM;
        double distance = calculator.distanceKm(0, 0, 0, 180);
        assertThat(distance).isNotNaN().isCloseTo(halfCircumference, within(1e-6));
    }
}
