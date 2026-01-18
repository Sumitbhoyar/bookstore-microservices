package com.bookstore.catalog.controller;

import com.bookstore.catalog.domain.Product;
import com.bookstore.catalog.service.ProductService;
import io.micrometer.core.annotation.Timed;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * REST Controller for Product operations.
 * Provides endpoints for product management, search, and catalog browsing.
 */
@RestController
@RequestMapping("/api/v1/products")
@Timed(value = "catalog.controller", percentiles = {0.5, 0.95, 0.99})
public class ProductController {

    private static final Logger logger = LoggerFactory.getLogger(ProductController.class);

    private final ProductService productService;

    @Autowired
    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    /**
     * GET /api/v1/products
     * Get active products with pagination.
     */
    @GetMapping
    @Timed(value = "catalog.get-products", percentiles = {0.5, 0.95, 0.99})
    public ResponseEntity<?> getProducts(@RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "20") int size) {
        String correlationId = getCorrelationId();

        logger.debug("[{}] Getting products (page: {}, size: {})", correlationId, page, size);

        Pageable pageable = PageRequest.of(page, Math.min(size, 100));
        Page<Product> products = productService.getActiveProducts(pageable, correlationId);

        return ResponseEntity.ok(new ProductPageResponse(products));
    }

    /**
     * GET /api/v1/products/{productId}
     * Get product by ID.
     */
    @GetMapping("/{productId}")
    @Timed(value = "catalog.get-product", percentiles = {0.5, 0.95, 0.99})
    public ResponseEntity<?> getProduct(@PathVariable UUID productId) {
        String correlationId = getCorrelationId();

        logger.debug("[{}] Getting product: {}", correlationId, productId);

        return productService.getProductById(productId, correlationId)
            .map(product -> ResponseEntity.ok(new ProductResponse(product)))
            .orElse(ResponseEntity.notFound().build());
    }

    /**
     * GET /api/v1/products/sku/{sku}
     * Get product by SKU.
     */
    @GetMapping("/sku/{sku}")
    @Timed(value = "catalog.get-product-by-sku", percentiles = {0.5, 0.95, 0.99})
    public ResponseEntity<?> getProductBySku(@PathVariable String sku) {
        String correlationId = getCorrelationId();

        logger.debug("[{}] Getting product by SKU: {}", correlationId, sku);

        return productService.getProductBySku(sku, correlationId)
            .map(product -> ResponseEntity.ok(new ProductResponse(product)))
            .orElse(ResponseEntity.notFound().build());
    }

    /**
     * GET /api/v1/products/category/{categoryId}
     * Get products by category.
     */
    @GetMapping("/category/{categoryId}")
    @Timed(value = "catalog.get-products-by-category", percentiles = {0.5, 0.95, 0.99})
    public ResponseEntity<?> getProductsByCategory(@PathVariable UUID categoryId,
                                                 @RequestParam(defaultValue = "0") int page,
                                                 @RequestParam(defaultValue = "20") int size) {
        String correlationId = getCorrelationId();

        logger.debug("[{}] Getting products by category: {} (page: {}, size: {})",
                    correlationId, categoryId, page, size);

        Pageable pageable = PageRequest.of(page, Math.min(size, 100));
        Page<Product> products = productService.getProductsByCategory(categoryId, pageable, correlationId);

        return ResponseEntity.ok(new ProductPageResponse(products));
    }

    /**
     * GET /api/v1/products/featured
     * Get featured products.
     */
    @GetMapping("/featured")
    @Timed(value = "catalog.get-featured-products", percentiles = {0.5, 0.95, 0.99})
    public ResponseEntity<?> getFeaturedProducts(@RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "12") int size) {
        String correlationId = getCorrelationId();

        logger.debug("[{}] Getting featured products (page: {}, size: {})", correlationId, page, size);

        Pageable pageable = PageRequest.of(page, Math.min(size, 50));
        Page<Product> products = productService.getFeaturedProducts(pageable, correlationId);

        return ResponseEntity.ok(new ProductPageResponse(products));
    }

    /**
     * GET /api/v1/products/search
     * Search products.
     */
    @GetMapping("/search")
    @Timed(value = "catalog.search-products", percentiles = {0.5, 0.95, 0.99})
    public ResponseEntity<?> searchProducts(@RequestParam String q,
                                          @RequestParam(defaultValue = "0") int page,
                                          @RequestParam(defaultValue = "20") int size) {
        String correlationId = getCorrelationId();

        logger.debug("[{}] Searching products: '{}' (page: {}, size: {})", correlationId, q, page, size);

        if (q == null || q.trim().isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(new ErrorResponse("INVALID_QUERY", "Search query cannot be empty"));
        }

        Pageable pageable = PageRequest.of(page, Math.min(size, 100));
        Page<Product> products = productService.searchProducts(q, pageable, correlationId);

        return ResponseEntity.ok(new ProductPageResponse(products));
    }

    /**
     * GET /api/v1/products/advanced-search
     * Advanced search with multiple filters.
     */
    @GetMapping("/advanced-search")
    @Timed(value = "catalog.advanced-search", percentiles = {0.5, 0.95, 0.99})
    public ResponseEntity<?> advancedSearch(@RequestParam(required = false) UUID categoryId,
                                          @RequestParam(required = false) BigDecimal minPrice,
                                          @RequestParam(required = false) BigDecimal maxPrice,
                                          @RequestParam(required = false) String author,
                                          @RequestParam(required = false) String publisher,
                                          @RequestParam(required = false) Boolean inStock,
                                          @RequestParam(defaultValue = "0") int page,
                                          @RequestParam(defaultValue = "20") int size) {
        String correlationId = getCorrelationId();

        logger.debug("[{}] Advanced search - category: {}, price: {}-{}, author: {}, publisher: {}, inStock: {} (page: {}, size: {})",
                    correlationId, categoryId, minPrice, maxPrice, author, publisher, inStock, page, size);

        Pageable pageable = PageRequest.of(page, Math.min(size, 100));
        Page<Product> products = productService.advancedSearch(categoryId, minPrice, maxPrice,
                                                             author, publisher, inStock, pageable, correlationId);

        return ResponseEntity.ok(new ProductPageResponse(products));
    }

    /**
     * POST /api/v1/products
     * Create a new product.
     */
    @PostMapping
    @Timed(value = "catalog.create-product", percentiles = {0.5, 0.95, 0.99})
    public ResponseEntity<?> createProduct(@Valid @RequestBody CreateProductRequest request) {
        String correlationId = getCorrelationId();

        logger.info("[{}] Creating product: {}", correlationId, request.getSku());

        try {
            Product product = createProductFromRequest(request);
            Product savedProduct = productService.createProduct(product, correlationId);

            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(new ProductResponse(savedProduct));
        } catch (IllegalArgumentException e) {
            logger.warn("[{}] Product creation failed: {}", correlationId, e.getMessage());
            return ResponseEntity.badRequest()
                    .body(new ErrorResponse("VALIDATION_ERROR", e.getMessage()));
        }
    }

    /**
     * PUT /api/v1/products/{productId}
     * Update an existing product.
     */
    @PutMapping("/{productId}")
    @Timed(value = "catalog.update-product", percentiles = {0.5, 0.95, 0.99})
    public ResponseEntity<?> updateProduct(@PathVariable UUID productId,
                                         @Valid @RequestBody UpdateProductRequest request) {
        String correlationId = getCorrelationId();

        logger.info("[{}] Updating product: {}", correlationId, productId);

        try {
            Product product = createProductFromRequest(request);
            Product savedProduct = productService.updateProduct(productId, product, correlationId);

            return ResponseEntity.ok(new ProductResponse(savedProduct));
        } catch (IllegalArgumentException e) {
            logger.warn("[{}] Product update failed for {}: {}", correlationId, productId, e.getMessage());
            return ResponseEntity.badRequest()
                    .body(new ErrorResponse("VALIDATION_ERROR", e.getMessage()));
        }
    }

    /**
     * DELETE /api/v1/products/{productId}
     * Delete a product.
     */
    @DeleteMapping("/{productId}")
    @Timed(value = "catalog.delete-product", percentiles = {0.5, 0.95, 0.99})
    public ResponseEntity<?> deleteProduct(@PathVariable UUID productId) {
        String correlationId = getCorrelationId();

        logger.info("[{}] Deleting product: {}", correlationId, productId);

        try {
            productService.deleteProduct(productId, correlationId);
            return ResponseEntity.ok(new SuccessResponse("Product deleted successfully"));
        } catch (IllegalArgumentException e) {
            logger.warn("[{}] Product deletion failed for {}: {}", correlationId, productId, e.getMessage());
            return ResponseEntity.badRequest()
                    .body(new ErrorResponse("NOT_FOUND", e.getMessage()));
        }
    }

    /**
     * GET /api/v1/products/analytics
     * Get product analytics.
     */
    @GetMapping("/analytics")
    @Timed(value = "catalog.get-analytics", percentiles = {0.5, 0.95, 0.99})
    public ResponseEntity<?> getProductAnalytics() {
        String correlationId = getCorrelationId();

        logger.debug("[{}] Getting product analytics", correlationId);

        ProductService.ProductAnalytics analytics = productService.getProductAnalytics(correlationId);

        return ResponseEntity.ok(new ProductAnalyticsResponse(analytics));
    }

    /**
     * GET /api/v1/products/inventory/low-stock
     * Get low stock products.
     */
    @GetMapping("/inventory/low-stock")
    @Timed(value = "catalog.get-low-stock", percentiles = {0.5, 0.95, 0.99})
    public ResponseEntity<?> getLowStockProducts(@RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "20") int size) {
        String correlationId = getCorrelationId();

        logger.debug("[{}] Getting low stock products (page: {}, size: {})", correlationId, page, size);

        Pageable pageable = PageRequest.of(page, Math.min(size, 100));
        Page<Product> products = productService.getLowStockProducts(pageable, correlationId);

        return ResponseEntity.ok(new ProductPageResponse(products));
    }

    /**
     * GET /api/v1/products/inventory/out-of-stock
     * Get out of stock products.
     */
    @GetMapping("/inventory/out-of-stock")
    @Timed(value = "catalog.get-out-of-stock", percentiles = {0.5, 0.95, 0.99})
    public ResponseEntity<?> getOutOfStockProducts(@RequestParam(defaultValue = "0") int page,
                                                 @RequestParam(defaultValue = "20") int size) {
        String correlationId = getCorrelationId();

        logger.debug("[{}] Getting out of stock products (page: {}, size: {})", correlationId, page, size);

        Pageable pageable = PageRequest.of(page, Math.min(size, 100));
        Page<Product> products = productService.getOutOfStockProducts(pageable, correlationId);

        return ResponseEntity.ok(new ProductPageResponse(products));
    }

    // Helper methods

    private String getCorrelationId() {
        String correlationId = MDC.get("correlationId");
        if (correlationId == null) {
            correlationId = UUID.randomUUID().toString();
            MDC.put("correlationId", correlationId);
        }
        return correlationId;
    }

    private Product createProductFromRequest(CreateProductRequest request) {
        Product product = new Product(request.getSku(), request.getTitle());
        product.setSubtitle(request.getSubtitle());
        product.setDescription(request.getDescription());
        product.setIsbn(request.getIsbn());
        product.setIsbn13(request.getIsbn13());
        product.setAuthors(request.getAuthors());
        product.setPublisher(request.getPublisher());
        product.setPublicationDate(request.getPublicationDate());
        product.setEdition(request.getEdition());
        product.setLanguage(request.getLanguage());
        product.setPages(request.getPages());
        product.setDimensions(request.getDimensions());
        product.setWeight(request.getWeight());
        product.setFormat(request.getFormat());
        product.setTags(request.getTags());
        product.setImages(request.getImages());
        product.setFeaturedImageUrl(request.getFeaturedImageUrl());
        product.setIsActive(request.getIsActive() != null ? request.getIsActive() : true);
        product.setIsFeatured(request.getIsFeatured() != null ? request.getIsFeatured() : false);
        return product;
    }

    // Request/Response DTOs

    public static class CreateProductRequest {
        private String sku;
        private String title;
        private String subtitle;
        private String description;
        private String isbn;
        private String isbn13;
        private String[] authors;
        private String publisher;
        private java.time.LocalDate publicationDate;
        private String edition;
        private String language;
        private Integer pages;
        private String dimensions;
        private Double weight;
        private String format;
        private String[] tags;
        private String[] images;
        private String featuredImageUrl;
        private Boolean isActive;
        private Boolean isFeatured;

        // Getters and setters
        public String getSku() { return sku; }
        public void setSku(String sku) { this.sku = sku; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getSubtitle() { return subtitle; }
        public void setSubtitle(String subtitle) { this.subtitle = subtitle; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String getIsbn() { return isbn; }
        public void setIsbn(String isbn) { this.isbn = isbn; }
        public String getIsbn13() { return isbn13; }
        public void setIsbn13(String isbn13) { this.isbn13 = isbn13; }
        public String[] getAuthors() { return authors; }
        public void setAuthors(String[] authors) { this.authors = authors; }
        public String getPublisher() { return publisher; }
        public void setPublisher(String publisher) { this.publisher = publisher; }
        public java.time.LocalDate getPublicationDate() { return publicationDate; }
        public void setPublicationDate(java.time.LocalDate publicationDate) { this.publicationDate = publicationDate; }
        public String getEdition() { return edition; }
        public void setEdition(String edition) { this.edition = edition; }
        public String getLanguage() { return language; }
        public void setLanguage(String language) { this.language = language; }
        public Integer getPages() { return pages; }
        public void setPages(Integer pages) { this.pages = pages; }
        public String getDimensions() { return dimensions; }
        public void setDimensions(String dimensions) { this.dimensions = dimensions; }
        public Double getWeight() { return weight; }
        public void setWeight(Double weight) { this.weight = weight; }
        public String getFormat() { return format; }
        public void setFormat(String format) { this.format = format; }
        public String[] getTags() { return tags; }
        public void setTags(String[] tags) { this.tags = tags; }
        public String[] getImages() { return images; }
        public void setImages(String[] images) { this.images = images; }
        public String getFeaturedImageUrl() { return featuredImageUrl; }
        public void setFeaturedImageUrl(String featuredImageUrl) { this.featuredImageUrl = featuredImageUrl; }
        public Boolean getIsActive() { return isActive; }
        public void setIsActive(Boolean isActive) { this.isActive = isActive; }
        public Boolean getIsFeatured() { return isFeatured; }
        public void setIsFeatured(Boolean isFeatured) { this.isFeatured = isFeatured; }
    }

    public static class UpdateProductRequest extends CreateProductRequest {
        // Same fields as CreateProductRequest
    }

    public static class ProductResponse {
        private UUID id;
        private String sku;
        private String title;
        private String subtitle;
        private String description;
        private String isbn;
        private String isbn13;
        private String[] authors;
        private String publisher;
        private java.time.LocalDate publicationDate;
        private String edition;
        private String language;
        private Integer pages;
        private String dimensions;
        private Double weight;
        private String format;
        private String[] tags;
        private String[] images;
        private String featuredImageUrl;
        private Boolean isActive;
        private Boolean isFeatured;
        private java.time.LocalDateTime createdAt;
        private java.time.LocalDateTime updatedAt;
        private boolean inStock;
        private boolean lowStock;
        private double averageRating;
        private int reviewCount;
        private String primaryAuthor;
        private String displayTitle;

        public ProductResponse(Product product) {
            this.id = product.getId();
            this.sku = product.getSku();
            this.title = product.getTitle();
            this.subtitle = product.getSubtitle();
            this.description = product.getDescription();
            this.isbn = product.getIsbn();
            this.isbn13 = product.getIsbn13();
            this.authors = product.getAuthors();
            this.publisher = product.getPublisher();
            this.publicationDate = product.getPublicationDate();
            this.edition = product.getEdition();
            this.language = product.getLanguage();
            this.pages = product.getPages();
            this.dimensions = product.getDimensions();
            this.weight = product.getWeight() != null ? product.getWeight().doubleValue() : null;
            this.format = product.getFormat();
            this.tags = product.getTags();
            this.images = product.getImages();
            this.featuredImageUrl = product.getFeaturedImageUrl();
            this.isActive = product.getIsActive();
            this.isFeatured = product.getIsFeatured();
            this.createdAt = product.getCreatedAt();
            this.updatedAt = product.getUpdatedAt();
            this.inStock = product.isInStock();
            this.lowStock = product.isLowStock();
            this.averageRating = product.getAverageRating();
            this.reviewCount = product.getReviewCount();
            this.primaryAuthor = product.getPrimaryAuthor();
            this.displayTitle = product.getDisplayTitle();
        }

        // Getters
        public UUID getId() { return id; }
        public String getSku() { return sku; }
        public String getTitle() { return title; }
        public String getSubtitle() { return subtitle; }
        public String getDescription() { return description; }
        public String getIsbn() { return isbn; }
        public String getIsbn13() { return isbn13; }
        public String[] getAuthors() { return authors; }
        public String getPublisher() { return publisher; }
        public java.time.LocalDate getPublicationDate() { return publicationDate; }
        public String getEdition() { return edition; }
        public String getLanguage() { return language; }
        public Integer getPages() { return pages; }
        public String getDimensions() { return dimensions; }
        public Double getWeight() { return weight; }
        public String getFormat() { return format; }
        public String[] getTags() { return tags; }
        public String[] getImages() { return images; }
        public String getFeaturedImageUrl() { return featuredImageUrl; }
        public Boolean getIsActive() { return isActive; }
        public Boolean getIsFeatured() { return isFeatured; }
        public java.time.LocalDateTime getCreatedAt() { return createdAt; }
        public java.time.LocalDateTime getUpdatedAt() { return updatedAt; }
        public boolean isInStock() { return inStock; }
        public boolean isLowStock() { return lowStock; }
        public double getAverageRating() { return averageRating; }
        public int getReviewCount() { return reviewCount; }
        public String getPrimaryAuthor() { return primaryAuthor; }
        public String getDisplayTitle() { return displayTitle; }
    }

    public static class ProductPageResponse {
        private java.util.List<ProductResponse> content;
        private int pageNumber;
        private int pageSize;
        private long totalElements;
        private int totalPages;
        private boolean first;
        private boolean last;

        public ProductPageResponse(Page<Product> page) {
            this.content = page.getContent().stream()
                    .map(ProductResponse::new)
                    .toList();
            this.pageNumber = page.getNumber();
            this.pageSize = page.getSize();
            this.totalElements = page.getTotalElements();
            this.totalPages = page.getTotalPages();
            this.first = page.isFirst();
            this.last = page.isLast();
        }

        // Getters
        public java.util.List<ProductResponse> getContent() { return content; }
        public int getPageNumber() { return pageNumber; }
        public int getPageSize() { return pageSize; }
        public long getTotalElements() { return totalElements; }
        public int getTotalPages() { return totalPages; }
        public boolean isFirst() { return first; }
        public boolean isLast() { return last; }
    }

    public static class ProductAnalyticsResponse {
        private long totalProducts;
        private long featuredProducts;
        private long productsWithReviews;
        private long lowStockVariants;
        private long outOfStockVariants;
        private java.math.BigDecimal totalInventoryValue;

        public ProductAnalyticsResponse(ProductService.ProductAnalytics analytics) {
            this.totalProducts = analytics.getTotalProducts();
            this.featuredProducts = analytics.getFeaturedProducts();
            this.productsWithReviews = analytics.getProductsWithReviews();
            this.lowStockVariants = analytics.getLowStockVariants();
            this.outOfStockVariants = analytics.getOutOfStockVariants();
            this.totalInventoryValue = analytics.getTotalInventoryValue();
        }

        // Getters
        public long getTotalProducts() { return totalProducts; }
        public long getFeaturedProducts() { return featuredProducts; }
        public long getProductsWithReviews() { return productsWithReviews; }
        public long getLowStockVariants() { return lowStockVariants; }
        public long getOutOfStockVariants() { return outOfStockVariants; }
        public java.math.BigDecimal getTotalInventoryValue() { return totalInventoryValue; }
    }

    public static class ErrorResponse {
        private String error;
        private String message;

        public ErrorResponse(String error, String message) {
            this.error = error;
            this.message = message;
        }

        public String getError() { return error; }
        public String getMessage() { return message; }
    }

    public static class SuccessResponse {
        private String message;

        public SuccessResponse(String message) {
            this.message = message;
        }

        public String getMessage() { return message; }
    }
}