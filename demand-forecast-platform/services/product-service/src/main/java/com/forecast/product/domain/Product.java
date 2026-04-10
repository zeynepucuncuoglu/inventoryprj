package com.forecast.product.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Core domain object. No JPA, no Spring, no Lombok here.
 * This is the source of truth for what a Product IS.
 * All business rules live here — not in the service layer.
 */
public class Product {

    private final UUID id;
    private String name;
    private String sku;
    private String category;
    private BigDecimal price;
    private int stockQuantity;
    private final Instant createdAt;
    private Instant updatedAt;

    // Called when creating a brand-new product
    public static Product create(String name, String sku, String category,
                                  BigDecimal price, int initialStock) {
        if (initialStock < 0) {
            throw new IllegalArgumentException("Initial stock cannot be negative");
        }
        if (price == null || price.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Price must be non-negative");
        }
        return new Product(UUID.randomUUID(), name, sku, category, price, initialStock,
                Instant.now(), Instant.now());
    }

    // Called when reconstituting from persistence
    public static Product reconstitute(UUID id, String name, String sku, String category,
                                        BigDecimal price, int stockQuantity,
                                        Instant createdAt, Instant updatedAt) {
        return new Product(id, name, sku, category, price, stockQuantity, createdAt, updatedAt);
    }

    private Product(UUID id, String name, String sku, String category,
                    BigDecimal price, int stockQuantity,
                    Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.name = name;
        this.sku = sku;
        this.category = category;
        this.price = price;
        this.stockQuantity = stockQuantity;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public void adjustStock(int delta) {
        int newQuantity = this.stockQuantity + delta;
        if (newQuantity < 0) {
            throw new InsufficientStockException(
                    String.format("Cannot reduce stock below zero. Current: %d, Delta: %d",
                            stockQuantity, delta));
        }
        this.stockQuantity = newQuantity;
        this.updatedAt = Instant.now();
    }

    public boolean isLowStock(int threshold) {
        return this.stockQuantity <= threshold;
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getSku() { return sku; }
    public String getCategory() { return category; }
    public BigDecimal getPrice() { return price; }
    public int getStockQuantity() { return stockQuantity; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
