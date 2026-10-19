package com.dispatch.util;

/**
 * Computes the distance between two geographic coordinates.
 * Abstracted so the dispatch strategy can be tested with, or switched to, another metric
 * (e.g. road distance from a routing API).
 */
public interface DistanceCalculator {

    /**
     * @return distance in kilometres between (lat1, lon1) and (lat2, lon2), given in decimal degrees
     */
    double distanceKm(double lat1, double lon1, double lat2, double lon2);
}
