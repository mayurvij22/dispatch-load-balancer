package com.dispatch.strategy;

import com.dispatch.entity.Order;

/**
 * An order no vehicle could take, with a human-readable reason.
 */
public record UnassignedOrder(Order order, String reason) {
}
