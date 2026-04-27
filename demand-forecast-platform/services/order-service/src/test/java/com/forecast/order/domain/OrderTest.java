package com.forecast.order.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class OrderTest {

    private static OrderItem item(int quantity, String price) {
        return OrderItem.of(UUID.randomUUID(), "SKU-001", quantity, new BigDecimal(price));
    }

    @Test
    void create_setsInitialStateCorrectly() {
        UUID customerId = UUID.randomUUID();
        Order order = Order.create(customerId, List.of(item(2, "10.00")));

        assertThat(order.getId()).isNotNull();
        assertThat(order.getCustomerId()).isEqualTo(customerId);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(order.getItems()).hasSize(1);
    }

    @Test
    void create_throwsWhenItemListIsEmpty() {
        assertThatThrownBy(() -> Order.create(UUID.randomUUID(), List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least one item");
    }

    @Test
    void create_throwsWhenItemListIsNull() {
        assertThatThrownBy(() -> Order.create(UUID.randomUUID(), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void total_sumItemSubtotals() {
        Order order = Order.create(UUID.randomUUID(), List.of(
                item(2, "10.00"),
                item(3, "5.00")
        ));

        assertThat(order.total()).isEqualByComparingTo("35.00");
    }

    @Test
    void confirm_transitionsPendingToConfirmed() {
        Order order = Order.create(UUID.randomUUID(), List.of(item(1, "9.99")));

        order.confirm();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    void confirm_throwsWhenNotPending() {
        Order order = Order.create(UUID.randomUUID(), List.of(item(1, "9.99")));
        order.confirm();

        assertThatThrownBy(order::confirm)
                .isInstanceOf(InvalidOrderStateException.class)
                .hasMessageContaining("PENDING");
    }

    @Test
    void ship_transitionsConfirmedToShipped() {
        Order order = Order.create(UUID.randomUUID(), List.of(item(1, "9.99")));
        order.confirm();

        order.ship();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.SHIPPED);
    }

    @Test
    void ship_throwsWhenNotConfirmed() {
        Order order = Order.create(UUID.randomUUID(), List.of(item(1, "9.99")));

        assertThatThrownBy(order::ship)
                .isInstanceOf(InvalidOrderStateException.class)
                .hasMessageContaining("CONFIRMED");
    }

    @Test
    void deliver_transitionsShippedToDelivered() {
        Order order = Order.create(UUID.randomUUID(), List.of(item(1, "9.99")));
        order.confirm();
        order.ship();

        order.deliver();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.DELIVERED);
    }

    @Test
    void deliver_throwsWhenNotShipped() {
        Order order = Order.create(UUID.randomUUID(), List.of(item(1, "9.99")));
        order.confirm();

        assertThatThrownBy(order::deliver)
                .isInstanceOf(InvalidOrderStateException.class)
                .hasMessageContaining("SHIPPED");
    }

    @Test
    void cancel_transitionsPendingToCancelled() {
        Order order = Order.create(UUID.randomUUID(), List.of(item(1, "9.99")));

        order.cancel();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void cancel_transitionsConfirmedToCancelled() {
        Order order = Order.create(UUID.randomUUID(), List.of(item(1, "9.99")));
        order.confirm();

        order.cancel();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void cancel_throwsWhenShipped() {
        Order order = Order.create(UUID.randomUUID(), List.of(item(1, "9.99")));
        order.confirm();
        order.ship();

        assertThatThrownBy(order::cancel)
                .isInstanceOf(InvalidOrderStateException.class)
                .hasMessageContaining("SHIPPED");
    }

    @Test
    void cancel_throwsWhenDelivered() {
        Order order = Order.create(UUID.randomUUID(), List.of(item(1, "9.99")));
        order.confirm();
        order.ship();
        order.deliver();

        assertThatThrownBy(order::cancel)
                .isInstanceOf(InvalidOrderStateException.class)
                .hasMessageContaining("DELIVERED");
    }

    @Test
    void cancel_throwsWhenAlreadyCancelled() {
        Order order = Order.create(UUID.randomUUID(), List.of(item(1, "9.99")));
        order.cancel();

        assertThatThrownBy(order::cancel)
                .isInstanceOf(InvalidOrderStateException.class)
                .hasMessageContaining("already cancelled");
    }

    @Test
    void getItems_returnsUnmodifiableList() {
        Order order = Order.create(UUID.randomUUID(), List.of(item(1, "9.99")));

        assertThatThrownBy(() -> order.getItems().add(item(1, "5.00")))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
