package com.forecast.order.domain;

/**
 * Valid order lifecycle transitions:
 * PENDING → CONFIRMED → SHIPPED → DELIVERED
 * PENDING → CANCELLED
 * CONFIRMED → CANCELLED
 */
public enum OrderStatus {
    PENDING,
    CONFIRMED,
    SHIPPED,
    DELIVERED,
    CANCELLED
}
