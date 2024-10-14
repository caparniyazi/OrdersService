package com.appsdeveloperblog.estore.ordersservice.command.commands;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.axonframework.modelling.command.TargetAggregateIdentifier;
import org.springframework.context.annotation.ComponentScan;

@Data
@AllArgsConstructor
@ComponentScan
public class ApproveOrderCommand {
    @TargetAggregateIdentifier
    private final String orderId;
}
