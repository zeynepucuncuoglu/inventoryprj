package com.forecast.product.infrastructure.persistence;

import com.forecast.product.domain.Product;
import com.forecast.product.domain.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Adapter: implements the domain port using JPA.
 * Translates between domain Product ↔ ProductEntity.
 * If we swap JPA for MongoDB tomorrow, only this class changes.
 */
@Component
@RequiredArgsConstructor
public class ProductRepositoryAdapter implements ProductRepository {

    private final ProductJpaRepository jpa;

    @Override
    public Product save(Product product) {
        // For existing entities, load the managed entity first so JPA keeps the @Version
        // value — without this, saving a builder-constructed entity with null version
        // would trigger INSERT (duplicate key) instead of UPDATE.
        ProductEntity entity = jpa.findById(product.getId())
                .map(existing -> {
                    existing.setName(product.getName());
                    existing.setSku(product.getSku());
                    existing.setCategory(product.getCategory());
                    existing.setPrice(product.getPrice());
                    existing.setStockQuantity(product.getStockQuantity());
                    return existing;
                })
                .orElseGet(() -> toEntity(product));
        return toDomain(jpa.save(entity));
    }

    @Override
    public Optional<Product> findById(UUID id) {
        return jpa.findById(id).map(this::toDomain);
    }

    @Override
    public Optional<Product> findBySku(String sku) {
        return jpa.findBySku(sku).map(this::toDomain);
    }

    @Override
    public List<Product> findAll() {
        return jpa.findAll().stream().map(this::toDomain).toList();
    }

    @Override
    public List<Product> findByCategory(String category) {
        return jpa.findByCategory(category).stream().map(this::toDomain).toList();
    }

    @Override
    public void deleteById(UUID id) {
        jpa.deleteById(id);
    }

    @Override
    public boolean existsBySku(String sku) {
        return jpa.existsBySku(sku);
    }

    private ProductEntity toEntity(Product product) {
        return ProductEntity.builder()
                .id(product.getId())
                .name(product.getName())
                .sku(product.getSku())
                .category(product.getCategory())
                .price(product.getPrice())
                .stockQuantity(product.getStockQuantity())
                .createdAt(product.getCreatedAt())
                .updatedAt(product.getUpdatedAt())
                .build();
    }

    private Product toDomain(ProductEntity entity) {
        return Product.reconstitute(
                entity.getId(),
                entity.getName(),
                entity.getSku(),
                entity.getCategory(),
                entity.getPrice(),
                entity.getStockQuantity(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
