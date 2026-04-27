package com.forecast.product.infrastructure.persistence;

import com.forecast.product.domain.Product;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import(ProductRepositoryAdapter.class)
class ProductRepositoryIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    TestEntityManager entityManager;

    @Autowired
    ProductRepositoryAdapter repository;

    private static Product newProduct(String sku, String category) {
        return Product.create("Test Product", sku, category, new BigDecimal("19.99"), 100);
    }

    @Test
    void save_andFindById_roundtrip() {
        Product saved = repository.save(newProduct("SKU-001", "Electronics"));

        Optional<Product> found = repository.findById(saved.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getSku()).isEqualTo("SKU-001");
        assertThat(found.get().getStockQuantity()).isEqualTo(100);
        assertThat(found.get().getPrice()).isEqualByComparingTo("19.99");
    }

    @Test
    void findBySku_returnsProduct_whenExists() {
        repository.save(newProduct("SKU-FIND", "Books"));

        Optional<Product> found = repository.findBySku("SKU-FIND");

        assertThat(found).isPresent();
        assertThat(found.get().getCategory()).isEqualTo("Books");
    }

    @Test
    void findBySku_returnsEmpty_whenNotExists() {
        Optional<Product> found = repository.findBySku("NONEXISTENT");

        assertThat(found).isEmpty();
    }

    @Test
    void existsBySku_returnsTrue_whenSkuTaken() {
        repository.save(newProduct("SKU-EXISTS", "Tools"));

        assertThat(repository.existsBySku("SKU-EXISTS")).isTrue();
    }

    @Test
    void existsBySku_returnsFalse_whenSkuFree() {
        assertThat(repository.existsBySku("SKU-FREE-123")).isFalse();
    }

    @Test
    void findByCategory_returnsOnlyMatchingProducts() {
        repository.save(newProduct("SKU-A1", "Clothing"));
        repository.save(newProduct("SKU-A2", "Clothing"));
        repository.save(newProduct("SKU-B1", "Tools"));

        List<Product> clothing = repository.findByCategory("Clothing");

        assertThat(clothing).hasSize(2);
        assertThat(clothing).extracting(Product::getCategory).containsOnly("Clothing");
    }

    @Test
    void deleteById_removesProduct() {
        Product saved = repository.save(newProduct("SKU-DEL", "Other"));

        repository.deleteById(saved.getId());

        assertThat(repository.findById(saved.getId())).isEmpty();
    }

    @Test
    void save_persistsStockAdjustment() {
        Product product = repository.save(newProduct("SKU-STOCK", "Electronics"));
        // Flush and evict so the next save doesn't conflict with the cached entity
        entityManager.flush();
        entityManager.clear();

        product.adjustStock(-30);
        repository.save(product);
        entityManager.flush();
        entityManager.clear();

        Product reloaded = repository.findById(product.getId()).orElseThrow();
        assertThat(reloaded.getStockQuantity()).isEqualTo(70);
    }
}
