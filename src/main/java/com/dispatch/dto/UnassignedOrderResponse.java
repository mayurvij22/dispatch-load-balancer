package com.dispatch.dto;

/**
 * An order that could not be placed on any vehicle, with the reason why.
 */
public record UnassignedOrderResponse(String orderId, String reason) {
}
