package com.dispatch.service.impl;

import com.dispatch.dto.OrderRequest;
import com.dispatch.entity.Order;
import com.dispatch.mapper.OrderMapper;
import com.dispatch.repository.OrderRepository;
import com.dispatch.service.OrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class OrderServiceImpl implements OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderServiceImpl.class);

    private final OrderRepository orderRepository;
    private final OrderMapper orderMapper;

    public OrderServiceImpl(OrderRepository orderRepository, OrderMapper orderMapper) {
        this.orderRepository = orderRepository;
        this.orderMapper = orderMapper;
    }

    @Override
    @Transactional
    public int upsertOrders(List<OrderRequest> orders) {
        List<Order> entities = UpsertSupport.prepareUpsert(orders, OrderRequest::orderId, "orderId",
                orderRepository, Order::getOrderId, orderMapper::toEntity, orderMapper::updateEntity);
        orderRepository.saveAll(entities);
        log.info("Upserted {} order(s)", entities.size());
        return entities.size();
    }

    @Override
    @Transactional
    public void deleteAllOrders() {
        // Single DELETE statement rather than loading and deleting row by row
        orderRepository.deleteAllInBatch();
        log.info("Deleted all orders");
    }
}
