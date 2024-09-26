package com.appsdeveloperblog.estore.ordersservice.core.data;

import com.appsdeveloperblog.estore.ordersservice.core.model.OrderStatus;
import jakarta.persistence.*;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
@Entity
@Table(name = "orders")
public class OrderEntity implements Serializable {
    @Serial
    private static final long serialVersionUID = 44L;

    @Id
    @Column(unique = true)
    public String orderId;
    private String productId;
    private String userId;
    private int quantity;
    private String addressId;

    @Enumerated(EnumType.STRING)
    private OrderStatus orderStatus;
}
