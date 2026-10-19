package com.dispatch.service;

import com.dispatch.dto.OrderRequest;

import java.util.List;

/**
 * Manages delivery orders.
 */
public interface OrderService {

    /**
     * Inserts new orders and updates existing ones (matched by orderId) in one transaction.
     *
     * @return the number of orders saved
     * @throws com.dispatch.exception.DuplicateIdException if an orderId repeats within the request
     */
    int upsertOrders(List<OrderRequest> orders);

    /** Removes all orders. */
    void deleteAllOrders();
}
