package com.forecast.order.domain;

public interface OrderEventPort {
    void publishOrderPlaced(Order order);

    void publishOrderConfirmed(Order order);

    void publishOrderShipped(Order order);

    void publishOrderDelivered(Order order);

    void publishOrderCancelled(Order order);
}
