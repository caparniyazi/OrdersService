package com.appsdeveloperblog.estore.ordersservice.core;

import com.appsdeveloperblog.estore.ordersservice.command.OrderStatus;
import lombok.Data;

@Data
public class OrderCreatedEvent {
    private String orderId;
    private String userId;
    private String productId;
    private int quantity;
    private String addressId;
    private OrderStatus orderStatus;
}
