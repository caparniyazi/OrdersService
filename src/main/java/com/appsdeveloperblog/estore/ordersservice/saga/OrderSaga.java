package com.appsdeveloperblog.estore.ordersservice.saga;

import com.appsdeveloperblog.estore.core.commands.ProcessPaymentCommand;
import com.appsdeveloperblog.estore.core.commands.ReserveProductCommand;
import com.appsdeveloperblog.estore.core.events.PaymentProcessedEvent;
import com.appsdeveloperblog.estore.core.events.ProductReservedEvent;
import com.appsdeveloperblog.estore.core.model.User;
import com.appsdeveloperblog.estore.core.query.FetchUserPaymentDetailsQuery;
import com.appsdeveloperblog.estore.ordersservice.command.ApproveOrderCommand;
import com.appsdeveloperblog.estore.ordersservice.core.events.OrderApprovedEvent;
import com.appsdeveloperblog.estore.ordersservice.core.events.OrderCreatedEvent;
import org.axonframework.commandhandling.CommandCallback;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.CommandResultMessage;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.messaging.responsetypes.ResponseTypes;
import org.axonframework.modelling.saga.EndSaga;
import org.axonframework.modelling.saga.SagaEventHandler;
import org.axonframework.modelling.saga.SagaLifecycle;
import org.axonframework.modelling.saga.StartSaga;
import org.axonframework.queryhandling.QueryGateway;
import org.axonframework.spring.stereotype.Saga;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import javax.annotation.Nonnull;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

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
    @Autowired
    private transient QueryGateway queryGateway;
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

    @SagaEventHandler(associationProperty = "orderId")
    public void handle(ProductReservedEvent productReservedEvent) {
        // Process user payment
        LOGGER.info("ProductReservedEvent for productId {} and orderId {}",
                productReservedEvent.getProductId(), productReservedEvent.getOrderId());

        FetchUserPaymentDetailsQuery fetchUserPaymentDetailsQuery =
                new FetchUserPaymentDetailsQuery(productReservedEvent.getUserId());
        User userPaymentDetails = null;
        try {
            userPaymentDetails = queryGateway.query(fetchUserPaymentDetailsQuery, ResponseTypes.instanceOf(User.class)).join();
        } catch (Exception e) {
            LOGGER.error(e.getMessage());
            // Start compensating transaction.
            return;
        }
        if (userPaymentDetails == null) {
            // Start compensating transaction.
            return;
        }
        LOGGER.info("Successfully fetched user payment details for user " + userPaymentDetails.getFirstName());

        ProcessPaymentCommand processPaymentCommand = ProcessPaymentCommand.builder()
                .orderId(productReservedEvent.getOrderId())
                .paymentId(UUID.randomUUID().toString())
                .paymentDetails(userPaymentDetails.getPaymentDetails())
                .build();

        String result = null;
        try {
            result = commandGateway.sendAndWait(processPaymentCommand, 10, TimeUnit.SECONDS);
        } catch (Exception e) {
            // Start compensating transaction
            LOGGER.error(e.getMessage());
        }

        if (result != null) {
            LOGGER.info("ProcessPaymentCommand resulted in null. Initiating a compensating transaction.");
            // Start compensating transaction.
        }
    }

    @SagaEventHandler(associationProperty = "orderId")
    public void handle(PaymentProcessedEvent paymentProcessedEvent) {
        //  Send an ApproveOrderCommand
        ApproveOrderCommand approveOrderCommand = new ApproveOrderCommand(paymentProcessedEvent.getOrderId());
        // Publish approve order command.
        commandGateway.send(approveOrderCommand);
    }

    @EndSaga
    @SagaEventHandler(associationProperty = "orderId")
    public void handle(OrderApprovedEvent orderApprovedEvent) {
        LOGGER.info("Order is approved. OrderSaga is complete for orderId: " + orderApprovedEvent);
        //SagaLifecycle.end(); Maybe used if there is a conditional logic.
    }
}
