package com.dispatch.entity;

/**
 * Delivery priority of an order.
 * <p>
 * Declaration order matters: constants are declared from most to least urgent, so the
 * natural enum ordering ({@code HIGH < MEDIUM < LOW}) is the dispatch ordering.
 */
public enum Priority {
    HIGH,
    MEDIUM,
    LOW
}
