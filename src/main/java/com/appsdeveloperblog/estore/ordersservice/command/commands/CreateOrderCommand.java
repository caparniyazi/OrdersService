package com.appsdeveloperblog.estore.ordersservice.command.commands;

import com.appsdeveloperblog.estore.ordersservice.core.model.OrderStatus;
import lombok.Builder;
import lombok.Data;
import org.axonframework.modelling.command.TargetAggregateIdentifier;
import org.springframework.context.annotation.ComponentScan;

@Builder
@Data
@ComponentScan
public class CreateOrderCommand {
    // Data fields
    @TargetAggregateIdentifier
    private final String orderId;

    private final String userId;
    private final String productId;
    private final int quantity;
    private final String addressId;
    private final OrderStatus orderStatus;
}
