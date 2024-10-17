package com.appsdeveloperblog.estore.ordersservice.saga;

import com.appsdeveloperblog.estore.core.commands.CancelProductReservationCommand;
import com.appsdeveloperblog.estore.core.commands.ProcessPaymentCommand;
import com.appsdeveloperblog.estore.core.commands.ReserveProductCommand;
import com.appsdeveloperblog.estore.core.events.PaymentProcessedEvent;
import com.appsdeveloperblog.estore.core.events.ProductReservationCancelledEvent;
import com.appsdeveloperblog.estore.core.events.ProductReservedEvent;
import com.appsdeveloperblog.estore.core.model.User;
import com.appsdeveloperblog.estore.core.query.FetchUserPaymentDetailsQuery;
import com.appsdeveloperblog.estore.ordersservice.command.commands.ApproveOrderCommand;
import com.appsdeveloperblog.estore.ordersservice.command.commands.RejectOrderCommand;
import com.appsdeveloperblog.estore.ordersservice.core.events.OrderApprovedEvent;
import com.appsdeveloperblog.estore.ordersservice.core.events.OrderCreatedEvent;
import com.appsdeveloperblog.estore.ordersservice.core.events.OrderRejectedEvent;
import org.axonframework.commandhandling.CommandCallback;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.CommandResultMessage;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.deadline.DeadlineManager;
import org.axonframework.deadline.annotation.DeadlineHandler;
import org.axonframework.messaging.responsetypes.ResponseTypes;
import org.axonframework.modelling.saga.EndSaga;
import org.axonframework.modelling.saga.SagaEventHandler;
import org.axonframework.modelling.saga.StartSaga;
import org.axonframework.queryhandling.QueryGateway;
import org.axonframework.spring.stereotype.Saga;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import javax.annotation.Nonnull;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

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

    @Autowired
    private transient DeadlineManager deadlineManager;

    private static final Logger LOGGER = LoggerFactory.getLogger(OrderSaga.class);
    private final String PAYMENT_PROCESSING_TIMEOUT_DEADLINE = "payment-processing-deadline";
    private String scheduleId;

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
        } catch (Exception ex) {
            LOGGER.error(ex.getMessage());
            // Start compensating transaction.
            cancelProductReservation(productReservedEvent, ex.getMessage());
            return;
        }
        if (userPaymentDetails == null) {
            // Start compensating transaction.
            cancelProductReservation(productReservedEvent, "Could not fetch user payment details");
            return;
        }
        LOGGER.info("Successfully fetched user payment details for user " + userPaymentDetails.getFirstName());

        scheduleId = deadlineManager.schedule(Duration.of(120, ChronoUnit.SECONDS), PAYMENT_PROCESSING_TIMEOUT_DEADLINE, productReservedEvent);

        ProcessPaymentCommand processPaymentCommand = ProcessPaymentCommand.builder()
                .orderId(productReservedEvent.getOrderId())
                .paymentId(UUID.randomUUID().toString())
                .paymentDetails(userPaymentDetails.getPaymentDetails())
                .build();

        String result = null;
        try {
            // commandGateway.sendAndWait(processPaymentCommand, 10, TimeUnit.SECONDS);
            result = commandGateway.sendAndWait(processPaymentCommand);
        } catch (Exception ex) {
            // Start compensating transaction
            cancelProductReservation(productReservedEvent, ex.getMessage());
            return;
            //LOGGER.error(ex.getMessage());
        }

        if (result == null) {
            LOGGER.info("ProcessPaymentCommand resulted in null. Initiating a compensating transaction.");
            // Start compensating transaction.
            cancelProductReservation(productReservedEvent, "Could not user payment with provided payment details.");
        }
    }

    private void cancelProductReservation(ProductReservedEvent productReservedEvent, String reason) {
        cancelDeadline();
        CancelProductReservationCommand cancelProductReservationCommand =
                CancelProductReservationCommand.builder()
                        .orderId(productReservedEvent.getOrderId())
                        .productId(productReservedEvent.getProductId())
                        .quantity(productReservedEvent.getQuantity())
                        .userId(productReservedEvent.getUserId())
                        .build();

        commandGateway.send(cancelProductReservationCommand);
    }

    @SagaEventHandler(associationProperty = "orderId")
    public void handle(PaymentProcessedEvent paymentProcessedEvent) {
        cancelDeadline();
        //  Send an ApproveOrderCommand
        ApproveOrderCommand approveOrderCommand = new ApproveOrderCommand(paymentProcessedEvent.getOrderId());
        // Publish approve order command.
        commandGateway.send(approveOrderCommand);
    }

    private void cancelDeadline() {
        if (scheduleId != null) {
            deadlineManager.cancelSchedule("payment-processing-deadline", scheduleId);
            scheduleId = null;
        }
    }

    @EndSaga

    @SagaEventHandler(associationProperty = "orderId")
    public void handle(OrderApprovedEvent orderApprovedEvent) {
        LOGGER.info("Order is approved. OrderSaga is complete for orderId: " + orderApprovedEvent.getOrderId());
        //SagaLifecycle.end(); Maybe used if there is a conditional logic.
    }

    @SagaEventHandler(associationProperty = "orderId")
    public void handle(ProductReservationCancelledEvent productReservationCancelledEvent) {
        // Create and send a RejectOrderCommand
        RejectOrderCommand rejectOrderCommand = new RejectOrderCommand(productReservationCancelledEvent.getOrderId(),
                productReservationCancelledEvent.getReason());
        commandGateway.send(rejectOrderCommand);
    }

    @EndSaga
    @SagaEventHandler(associationProperty = "orderId")
    public void handle(OrderRejectedEvent orderRejectedEvent) {
        LOGGER.info("Successfully rejected order with id " + orderRejectedEvent.getOrderId());
    }

    @DeadlineHandler(deadlineName = PAYMENT_PROCESSING_TIMEOUT_DEADLINE)
    public void handlePaymentDeadline(ProductReservedEvent productReservedEvent) {
        LOGGER.info("Payment processing deadline took place. Sending a compensating command to cancel the product reservation.");
        cancelProductReservation(productReservedEvent, "Payment timeout");
    }
}
