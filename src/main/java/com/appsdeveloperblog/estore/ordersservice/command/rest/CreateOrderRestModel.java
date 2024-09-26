package com.appsdeveloperblog.estore.ordersservice.command.rest;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateOrderRestModel {
    // Data fields
    @NotBlank(message = "Order productId is a required field")
    private String productId;

    @Min(value = 1, message = "Price cannot be lower than 1")
    @Max(value = 5, message = "Price cannot be greater than 5")
    private int quantity;

    @NotBlank(message = "Order addressId is a required field")
    private String addressId;
}
