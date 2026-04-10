package com.forecast.product.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

// Spring Data JPA — only deals with ProductEntity, never with domain Product
interface ProductJpaRepository extends JpaRepository<ProductEntity, UUID> {
    Optional<ProductEntity> findBySku(String sku);
    List<ProductEntity> findByCategory(String category);
    boolean existsBySku(String sku);
}
