package com.appsdeveloperblog.estore.ordersservice.core.events;

import com.appsdeveloperblog.estore.ordersservice.core.model.OrderStatus;
import lombok.Value;

@Value
public class OrderRejectedEvent {
    String orderId;
    String reason;
    OrderStatus orderStatus = OrderStatus.REJECTED;
}
