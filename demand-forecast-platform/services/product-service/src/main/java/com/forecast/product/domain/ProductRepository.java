package com.forecast.product.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Port (interface) — the domain defines what persistence it needs.
 * The infrastructure layer provides the adapter (JPA implementation).
 * This inversion means domain code never imports JPA or SQL.
 */
public interface ProductRepository {
    Product save(Product product);
    Optional<Product> findById(UUID id);
    Optional<Product> findBySku(String sku);
    List<Product> findAll();
    List<Product> findByCategory(String category);
    void deleteById(UUID id);
    boolean existsBySku(String sku);
}
