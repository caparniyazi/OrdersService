package com.appsdeveloperblog.estore.ordersservice.core.events;

import com.appsdeveloperblog.estore.ordersservice.core.model.OrderStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderCreatedEvent {
    private String orderId; // This attribute is the association property for the saga method as well.
    private String productId;
    private String userId;
    private int quantity;
    private String addressId;
    private OrderStatus orderStatus;
}
