package com.forecast.order.application;

import com.forecast.order.application.dto.*;
import com.forecast.order.domain.*;
import com.forecast.order.infrastructure.messaging.OrderEventPublisher;
import com.forecast.order.domain.OrderStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock OrderRepository orderRepository;
    @Mock OrderEventPublisher eventPublisher;
    @InjectMocks OrderService orderService;

    private static PlaceOrderRequest placeRequest() {
        return new PlaceOrderRequest(
                UUID.randomUUID(),
                List.of(new OrderItemRequest(UUID.randomUUID(), "SKU-001", 2, new BigDecimal("10.00")))
        );
    }

    private static Order savedOrder() {
        return Order.create(
                UUID.randomUUID(),
                List.of(OrderItem.of(UUID.randomUUID(), "SKU-001", 2, new BigDecimal("10.00")))
        );
    }

    @Test
    void placeOrder_createsOrderAndPublishesEvent() {
        given(orderRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        OrderResponse response = orderService.placeOrder(placeRequest());

        assertThat(response.status()).isEqualTo(OrderStatus.PENDING);
        assertThat(response.items()).hasSize(1);
        then(orderRepository).should().save(any(Order.class));
        then(eventPublisher).should().publishOrderPlaced(any(Order.class));
    }

    @Test
    void getOrder_returnsResponse_whenFound() {
        UUID id = UUID.randomUUID();
        Order order = savedOrder();
        given(orderRepository.findById(id)).willReturn(Optional.of(order));

        OrderResponse response = orderService.getOrder(id);

        assertThat(response).isNotNull();
    }

    @Test
    void getOrder_throwsOrderNotFoundException_whenNotFound() {
        UUID id = UUID.randomUUID();
        given(orderRepository.findById(id)).willReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.getOrder(id))
                .isInstanceOf(OrderNotFoundException.class);
    }

    @Test
    void confirmOrder_transitionsStatusAndPublishesEvent() {
        UUID id = UUID.randomUUID();
        Order order = savedOrder();
        given(orderRepository.findById(id)).willReturn(Optional.of(order));
        given(orderRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        OrderResponse response = orderService.confirmOrder(id);

        assertThat(response.status()).isEqualTo(OrderStatus.CONFIRMED);
        then(eventPublisher).should().publishOrderConfirmed(any(Order.class));
    }

    @Test
    void confirmOrder_throwsOrderNotFoundException_whenNotFound() {
        UUID id = UUID.randomUUID();
        given(orderRepository.findById(id)).willReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.confirmOrder(id))
                .isInstanceOf(OrderNotFoundException.class);
    }

    @Test
    void shipOrder_transitionsStatusAndPublishesEvent() {
        UUID id = UUID.randomUUID();
        Order order = savedOrder();
        order.confirm();
        given(orderRepository.findById(id)).willReturn(Optional.of(order));
        given(orderRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        OrderResponse response = orderService.shipOrder(id);

        assertThat(response.status()).isEqualTo(OrderStatus.SHIPPED);
        then(eventPublisher).should().publishOrderShipped(any(Order.class));
    }

    @Test
    void cancelOrder_transitionsStatusAndPublishesEvent() {
        UUID id = UUID.randomUUID();
        Order order = savedOrder();
        given(orderRepository.findById(id)).willReturn(Optional.of(order));
        given(orderRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        OrderResponse response = orderService.cancelOrder(id);

        assertThat(response.status()).isEqualTo(OrderStatus.CANCELLED);
        then(eventPublisher).should().publishOrderCancelled(any(Order.class));
    }

    @Test
    void cancelOrder_throwsInvalidOrderStateException_whenShipped() {
        UUID id = UUID.randomUUID();
        Order order = savedOrder();
        order.confirm();
        order.ship();
        given(orderRepository.findById(id)).willReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.cancelOrder(id))
                .isInstanceOf(InvalidOrderStateException.class);
    }

    @Test
    void getOrdersByCustomer_returnsMappedResponses() {
        UUID customerId = UUID.randomUUID();
        given(orderRepository.findByCustomerId(customerId)).willReturn(List.of(savedOrder(), savedOrder()));

        List<OrderResponse> result = orderService.getOrdersByCustomer(customerId);

        assertThat(result).hasSize(2);
    }
}
