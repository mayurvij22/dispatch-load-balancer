package com.dispatch.util;

import org.springframework.stereotype.Component;

/**
 * Great-circle distance using the Haversine formula on a spherical Earth.
 * <pre>
 *   a = sin²(Δφ/2) + cos φ1 · cos φ2 · sin²(Δλ/2)
 *   d = 2R · atan2(√a, √(1−a))
 * </pre>
 * Accuracy is within ~0.5% of the true ellipsoidal distance, which is ample for dispatch planning.
 */
@Component
public class HaversineDistanceCalculator implements DistanceCalculator {

    /** Mean Earth radius in kilometres. */
    public static final double EARTH_RADIUS_KM = 6371.0;

    @Override
    public double distanceKm(double lat1, double lon1, double lat2, double lon2) {
        double phi1 = Math.toRadians(lat1);
        double phi2 = Math.toRadians(lat2);
        double deltaPhi = Math.toRadians(lat2 - lat1);
        double deltaLambda = Math.toRadians(lon2 - lon1);

        double sinHalfDeltaPhi = Math.sin(deltaPhi / 2);
        double sinHalfDeltaLambda = Math.sin(deltaLambda / 2);

        double a = sinHalfDeltaPhi * sinHalfDeltaPhi
                + Math.cos(phi1) * Math.cos(phi2) * sinHalfDeltaLambda * sinHalfDeltaLambda;
        // Clamp guards against a drifting slightly above 1 through floating-point error at antipodes
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(Math.max(0.0, 1 - a)));
        return EARTH_RADIUS_KM * c;
    }
}
