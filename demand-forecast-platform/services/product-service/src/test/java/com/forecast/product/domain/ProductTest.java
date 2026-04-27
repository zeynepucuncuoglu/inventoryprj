package com.forecast.product.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;

class ProductTest {

    @Test
    void create_setsInitialStateCorrectly() {
        Product product = Product.create("Widget", "SKU-001", "Electronics", new BigDecimal("19.99"), 100);

        assertThat(product.getId()).isNotNull();
        assertThat(product.getName()).isEqualTo("Widget");
        assertThat(product.getSku()).isEqualTo("SKU-001");
        assertThat(product.getCategory()).isEqualTo("Electronics");
        assertThat(product.getPrice()).isEqualByComparingTo("19.99");
        assertThat(product.getStockQuantity()).isEqualTo(100);
        assertThat(product.getCreatedAt()).isNotNull();
        assertThat(product.getUpdatedAt()).isNotNull();
    }

    @Test
    void create_throwsOnNegativeInitialStock() {
        assertThatThrownBy(() ->
                Product.create("Widget", "SKU-001", "Electronics", new BigDecimal("9.99"), -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("negative");
    }

    @Test
    void create_allowsZeroInitialStock() {
        Product product = Product.create("Widget", "SKU-001", "Electronics", new BigDecimal("9.99"), 0);
        assertThat(product.getStockQuantity()).isZero();
    }

    @Test
    void create_throwsOnNullPrice() {
        assertThatThrownBy(() ->
                Product.create("Widget", "SKU-001", "Electronics", null, 10))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void create_throwsOnNegativePrice() {
        assertThatThrownBy(() ->
                Product.create("Widget", "SKU-001", "Electronics", new BigDecimal("-0.01"), 10))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void adjustStock_increasesQuantityByDelta() {
        Product product = Product.create("Widget", "SKU-001", "Electronics", new BigDecimal("9.99"), 10);

        product.adjustStock(5);

        assertThat(product.getStockQuantity()).isEqualTo(15);
    }

    @Test
    void adjustStock_decreasesQuantityByDelta() {
        Product product = Product.create("Widget", "SKU-001", "Electronics", new BigDecimal("9.99"), 10);

        product.adjustStock(-3);

        assertThat(product.getStockQuantity()).isEqualTo(7);
    }

    @Test
    void adjustStock_allowsDrainingToZero() {
        Product product = Product.create("Widget", "SKU-001", "Electronics", new BigDecimal("9.99"), 5);

        product.adjustStock(-5);

        assertThat(product.getStockQuantity()).isZero();
    }

    @Test
    void adjustStock_throwsInsufficientStockException_whenResultWouldBeNegative() {
        Product product = Product.create("Widget", "SKU-001", "Electronics", new BigDecimal("9.99"), 3);

        assertThatThrownBy(() -> product.adjustStock(-4))
                .isInstanceOf(InsufficientStockException.class);
    }

    @Test
    void adjustStock_updatesTimestamp() throws InterruptedException {
        Product product = Product.create("Widget", "SKU-001", "Electronics", new BigDecimal("9.99"), 10);
        var before = product.getUpdatedAt();

        Thread.sleep(10);
        product.adjustStock(1);

        assertThat(product.getUpdatedAt()).isAfter(before);
    }

    @Test
    void isLowStock_returnsTrueWhenAtThreshold() {
        Product product = Product.create("Widget", "SKU-001", "Electronics", new BigDecimal("9.99"), 5);

        assertThat(product.isLowStock(5)).isTrue();
    }

    @Test
    void isLowStock_returnsTrueWhenBelowThreshold() {
        Product product = Product.create("Widget", "SKU-001", "Electronics", new BigDecimal("9.99"), 2);

        assertThat(product.isLowStock(5)).isTrue();
    }

    @Test
    void isLowStock_returnsFalseWhenAboveThreshold() {
        Product product = Product.create("Widget", "SKU-001", "Electronics", new BigDecimal("9.99"), 6);

        assertThat(product.isLowStock(5)).isFalse();
    }
}
