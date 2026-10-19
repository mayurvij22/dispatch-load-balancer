package com.dispatch.mapper;

import com.dispatch.dto.OrderRequest;
import com.dispatch.dto.OrderResponse;
import com.dispatch.entity.Order;
import org.springframework.stereotype.Component;

/**
 * Converts between order DTOs and the {@link Order} entity.
 */
@Component
public class OrderMapper {

    public Order toEntity(OrderRequest request) {
        return new Order(request.orderId(), request.latitude(), request.longitude(), request.address(),
                request.packageWeight(), request.priority());
    }

    /** Copies the request onto an already-persisted entity (upsert path). */
    public void updateEntity(Order order, OrderRequest request) {
        order.update(request.latitude(), request.longitude(), request.address(),
                request.packageWeight(), request.priority());
    }

    public OrderResponse toResponse(Order order) {
        return new OrderResponse(order.getOrderId(), order.getLatitude(), order.getLongitude(), order.getAddress(),
                order.getPackageWeight(), order.getPriority());
    }
}
