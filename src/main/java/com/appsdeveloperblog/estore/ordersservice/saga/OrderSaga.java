package com.appsdeveloperblog.estore.ordersservice.saga;

import com.appsdeveloperblog.estore.core.commands.ReserveProductCommand;
import com.appsdeveloperblog.estore.core.events.ProductReservedEvent;
import com.appsdeveloperblog.estore.ordersservice.core.events.OrderCreatedEvent;
import org.axonframework.commandhandling.CommandCallback;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.CommandResultMessage;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.modelling.saga.SagaEventHandler;
import org.axonframework.modelling.saga.StartSaga;
import org.axonframework.spring.stereotype.Saga;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import javax.annotation.Nonnull;

/**
 * Inform axon framework that this class is a saga class.
 * It manages the flow.
 * Saga is used to manage multiple operations within a single transaction.
 * Saga is an event handler component. It handles events
 * and dispatches commands.
 */
@Saga
public class OrderSaga {
    @Autowired
    private transient CommandGateway commandGateway;    // Transient so that it does not get serialized.
    private static final Logger LOGGER = LoggerFactory.getLogger(OrderSaga.class);

    /**
     * Handling OrderCreated event.
     *
     * @param orderCreatedEvent The event
     */
    @StartSaga  // This(OrderCreatedEvent) is the beginning of the saga life-cycle.
    @SagaEventHandler(associationProperty = "orderId")
    public void handle(OrderCreatedEvent orderCreatedEvent) {
        ReserveProductCommand reserveProductCommand =
                ReserveProductCommand.builder().
                        orderId(orderCreatedEvent.getOrderId()).
                        productId(orderCreatedEvent.getProductId()).
                        quantity(orderCreatedEvent.getQuantity()).
                        userId(orderCreatedEvent.getUserId()).build();
        LOGGER.info("OrderCreatedEvent for orderId {} and productId {}",
                orderCreatedEvent.getOrderId(), orderCreatedEvent.getProductId());

        commandGateway.send(reserveProductCommand,
                new CommandCallback<>() {
                    @Override
                    public void onResult(@Nonnull CommandMessage<? extends ReserveProductCommand> commandMessage,
                                         @Nonnull CommandResultMessage<?> commandResultMessage) {
                        if (commandResultMessage.isExceptional()) {
                            // Start a compensating transaction.
                            System.out.println();
                        }
                    }
                });
    }

    @SagaEventHandler(associationProperty = "productId")
    public void handle(ProductReservedEvent productReservedEvent) {
        // Process user payment
        LOGGER.info("OrderCreatedEvent for productId {} and orderId {}",
                productReservedEvent.getProductId(), productReservedEvent.getOrderId());
    }
}
