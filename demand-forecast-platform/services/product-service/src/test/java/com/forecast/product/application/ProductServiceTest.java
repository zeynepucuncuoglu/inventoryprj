package com.forecast.product.application;

import com.forecast.product.application.dto.AdjustStockRequest;
import com.forecast.product.application.dto.CreateProductRequest;
import com.forecast.product.application.dto.ProductResponse;
import com.forecast.product.domain.Product;
import com.forecast.product.domain.ProductRepository;
import com.forecast.product.infrastructure.messaging.ProductEventPublisher;
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
class ProductServiceTest {

    @Mock ProductRepository productRepository;
    @Mock ProductEventPublisher eventPublisher;
    @InjectMocks ProductService productService;

    private static CreateProductRequest createRequest() {
        return new CreateProductRequest("Widget", "SKU-001", "Electronics", new BigDecimal("9.99"), 50);
    }

    private static Product savedProduct(String sku) {
        return Product.create("Widget", sku, "Electronics", new BigDecimal("9.99"), 50);
    }

    @Test
    void createProduct_savesAndPublishesEvent() {
        given(productRepository.existsBySku("SKU-001")).willReturn(false);
        given(productRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        ProductResponse response = productService.createProduct(createRequest());

        assertThat(response.sku()).isEqualTo("SKU-001");
        assertThat(response.name()).isEqualTo("Widget");
        then(productRepository).should().save(any(Product.class));
        then(eventPublisher).should().publishProductCreated(any(Product.class));
    }

    @Test
    void createProduct_throwsDuplicateSkuException_whenSkuAlreadyExists() {
        given(productRepository.existsBySku("SKU-001")).willReturn(true);

        assertThatThrownBy(() -> productService.createProduct(createRequest()))
                .isInstanceOf(DuplicateSkuException.class)
                .hasMessageContaining("SKU-001");

        then(productRepository).should(never()).save(any());
        then(eventPublisher).shouldHaveNoInteractions();
    }

    @Test
    void getProduct_returnsResponse_whenFound() {
        UUID id = UUID.randomUUID();
        Product product = savedProduct("SKU-001");
        given(productRepository.findById(id)).willReturn(Optional.of(product));

        ProductResponse response = productService.getProduct(id);

        assertThat(response.sku()).isEqualTo("SKU-001");
    }

    @Test
    void getProduct_throwsProductNotFoundException_whenNotFound() {
        UUID id = UUID.randomUUID();
        given(productRepository.findById(id)).willReturn(Optional.empty());

        assertThatThrownBy(() -> productService.getProduct(id))
                .isInstanceOf(ProductNotFoundException.class);
    }

    @Test
    void getAllProducts_returnsMappedResponses() {
        given(productRepository.findAll()).willReturn(List.of(
                savedProduct("SKU-001"),
                savedProduct("SKU-002")
        ));

        List<ProductResponse> result = productService.getAllProducts();

        assertThat(result).hasSize(2);
    }

    @Test
    void adjustStock_appliesDeltaAndPublishesEvent() {
        UUID id = UUID.randomUUID();
        Product product = savedProduct("SKU-001");
        given(productRepository.findById(id)).willReturn(Optional.of(product));
        given(productRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        ProductResponse response = productService.adjustStock(id, new AdjustStockRequest(-5));

        assertThat(response.stockQuantity()).isEqualTo(45);
        then(eventPublisher).should().publishStockAdjusted(any(Product.class), eq(-5));
    }

    @Test
    void adjustStock_throwsProductNotFoundException_whenProductDoesNotExist() {
        UUID id = UUID.randomUUID();
        given(productRepository.findById(id)).willReturn(Optional.empty());

        assertThatThrownBy(() -> productService.adjustStock(id, new AdjustStockRequest(10)))
                .isInstanceOf(ProductNotFoundException.class);
    }

    @Test
    void deleteProduct_deletesById_whenFound() {
        UUID id = UUID.randomUUID();
        given(productRepository.findById(id)).willReturn(Optional.of(savedProduct("SKU-001")));

        productService.deleteProduct(id);

        then(productRepository).should().deleteById(id);
    }

    @Test
    void deleteProduct_throwsProductNotFoundException_whenNotFound() {
        UUID id = UUID.randomUUID();
        given(productRepository.findById(id)).willReturn(Optional.empty());

        assertThatThrownBy(() -> productService.deleteProduct(id))
                .isInstanceOf(ProductNotFoundException.class);
    }
}
