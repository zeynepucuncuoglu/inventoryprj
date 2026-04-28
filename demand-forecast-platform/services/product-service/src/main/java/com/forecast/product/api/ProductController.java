package com.forecast.product.api;

import com.forecast.product.application.ProductService;
import com.forecast.product.application.dto.AdjustStockRequest;
import com.forecast.product.application.dto.CreateProductRequest;
import com.forecast.product.application.dto.ProductResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
@Tag(name = "Products", description = "Product catalogue and inventory management")
public class ProductController {

    private final ProductService productService;

    @Operation(summary = "Create a product", description = "Creates a new product and publishes a product.created Kafka event.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Product created"),
            @ApiResponse(responseCode = "400", description = "Invalid request body"),
            @ApiResponse(responseCode = "409", description = "SKU already exists")
    })
    @PostMapping
    public ResponseEntity<ProductResponse> createProduct(
            @Valid @RequestBody CreateProductRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(productService.createProduct(request));
    }

    @Operation(summary = "Get a product by ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Product found"),
            @ApiResponse(responseCode = "404", description = "Product not found")
    })
    @GetMapping("/{id}")
    public ResponseEntity<ProductResponse> getProduct(
            @Parameter(description = "Product UUID") @PathVariable UUID id) {
        return ResponseEntity.ok(productService.getProduct(id));
    }

    @Operation(summary = "List products", description = "Returns all products, or filters by category if provided.")
    @ApiResponse(responseCode = "200", description = "Product list")
    @GetMapping
    public ResponseEntity<List<ProductResponse>> getAllProducts(
            @Parameter(description = "Filter by category (optional)") @RequestParam(required = false) String category) {
        List<ProductResponse> products = category != null
                ? productService.getProductsByCategory(category)
                : productService.getAllProducts();
        return ResponseEntity.ok(products);
    }

    @Operation(summary = "Adjust stock",
            description = "Applies a delta to current stock. Positive = restock, negative = deduct. " +
                    "Publishes a stock.adjusted Kafka event. Returns 400 if result would be negative.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Stock updated"),
            @ApiResponse(responseCode = "400", description = "Insufficient stock"),
            @ApiResponse(responseCode = "404", description = "Product not found")
    })
    @PatchMapping("/{id}/stock")
    public ResponseEntity<ProductResponse> adjustStock(
            @Parameter(description = "Product UUID") @PathVariable UUID id,
            @Valid @RequestBody AdjustStockRequest request) {
        return ResponseEntity.ok(productService.adjustStock(id, request));
    }

    @Operation(summary = "Delete a product")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Deleted"),
            @ApiResponse(responseCode = "404", description = "Product not found")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProduct(
            @Parameter(description = "Product UUID") @PathVariable UUID id) {
        productService.deleteProduct(id);
        return ResponseEntity.noContent().build();
    }
}
