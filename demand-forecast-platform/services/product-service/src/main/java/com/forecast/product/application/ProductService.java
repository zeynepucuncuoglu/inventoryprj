package com.forecast.product.application;

import com.forecast.product.application.dto.AdjustStockRequest;
import com.forecast.product.application.dto.CreateProductRequest;
import com.forecast.product.application.dto.ProductResponse;
import com.forecast.product.domain.Product;
import com.forecast.product.domain.ProductRepository;
import com.forecast.product.infrastructure.messaging.ProductEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Application service — orchestrates domain objects and side effects.
 * No business rules here; those belong in Product (domain).
 * This layer handles: transaction boundaries, event publishing, and mapping.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductEventPublisher eventPublisher;

    @Transactional
    public ProductResponse createProduct(CreateProductRequest request) {
        if (productRepository.existsBySku(request.sku())) {
            throw new DuplicateSkuException("SKU already exists: " + request.sku());
        }

        Product product = Product.create(
                request.name(),
                request.sku(),
                request.category(),
                request.price(),
                request.initialStock()
        );

        Product saved = productRepository.save(product);
        log.info("Product created: id={}, sku={}", saved.getId(), saved.getSku());

        eventPublisher.publishProductCreated(saved);

        return ProductResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public ProductResponse getProduct(UUID id) {
        return productRepository.findById(id)
                .map(ProductResponse::from)
                .orElseThrow(() -> new ProductNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> getAllProducts() {
        return productRepository.findAll().stream()
                .map(ProductResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> getProductsByCategory(String category) {
        return productRepository.findByCategory(category).stream()
                .map(ProductResponse::from)
                .toList();
    }

    @Transactional
    public ProductResponse adjustStock(UUID id, AdjustStockRequest request) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException(id));

        product.adjustStock(request.delta());  // domain enforces the rule

        Product saved = productRepository.save(product);
        log.info("Stock adjusted: id={}, delta={}, newQuantity={}",
                id, request.delta(), saved.getStockQuantity());

        eventPublisher.publishStockAdjusted(saved, request.delta());

        return ProductResponse.from(saved);
    }

    @Transactional
    public void deleteProduct(UUID id) {
        if (!productRepository.findById(id).isPresent()) {
            throw new ProductNotFoundException(id);
        }
        productRepository.deleteById(id);
        log.info("Product deleted: id={}", id);
    }
}
