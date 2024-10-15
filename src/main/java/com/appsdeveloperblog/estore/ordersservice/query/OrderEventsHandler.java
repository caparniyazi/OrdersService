package com.appsdeveloperblog.estore.ordersservice.query;

import com.appsdeveloperblog.estore.ordersservice.core.data.OrderEntity;
import com.appsdeveloperblog.estore.ordersservice.core.data.OrderRepository;
import com.appsdeveloperblog.estore.ordersservice.core.events.OrderApprovedEvent;
import com.appsdeveloperblog.estore.ordersservice.core.events.OrderCreatedEvent;
import com.appsdeveloperblog.estore.ordersservice.core.events.OrderRejectedEvent;
import com.appsdeveloperblog.estore.ordersservice.core.model.OrderStatus;
import org.axonframework.config.ProcessingGroup;
import org.axonframework.eventhandling.EventHandler;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Component;

@Component
@ProcessingGroup("order-group")

public class OrderEventsHandler {
    // Data fields
    private final OrderRepository orderRepository;

    public OrderEventsHandler(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @EventHandler
    public void on(OrderCreatedEvent event) throws Exception {
        OrderEntity orderEntity = new OrderEntity();
        BeanUtils.copyProperties(event, orderEntity);

        try {
            orderRepository.save(orderEntity);
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        }
    }

    @EventHandler
    public void on(OrderApprovedEvent event) throws Exception {
        OrderEntity orderEntity = orderRepository.findByOrderId(event.getOrderId());

        if (orderEntity == null) {
            // TODO: Do stg about it
            return;
        }
        orderEntity.setOrderStatus(event.getOrderStatus());
        orderRepository.save(orderEntity);
    }

    @EventHandler
    public void on(OrderRejectedEvent orderRejectedEvent) {
        OrderEntity orderEntity = orderRepository.findByOrderId(orderRejectedEvent.getOrderId());
        orderEntity.setOrderStatus(orderRejectedEvent.getOrderStatus());
        orderRepository.save(orderEntity);
    }
}
