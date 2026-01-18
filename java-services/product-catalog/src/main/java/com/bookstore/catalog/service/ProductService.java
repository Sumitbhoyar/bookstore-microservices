package com.bookstore.catalog.service;

import com.bookstore.catalog.domain.Product;
import com.bookstore.catalog.domain.ProductVariant;
import com.bookstore.catalog.repository.ProductRepository;
import com.bookstore.catalog.repository.ProductVariantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for managing products and their variants.
 */
@Service
@Transactional
public class ProductService {

    private static final Logger logger = LoggerFactory.getLogger(ProductService.class);

    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;
    private final EventPublisherService eventPublisher;
    private final AuditService auditService;

    @Autowired
    public ProductService(ProductRepository productRepository,
                         ProductVariantRepository variantRepository,
                         EventPublisherService eventPublisher,
                         AuditService auditService) {
        this.productRepository = productRepository;
        this.variantRepository = variantRepository;
        this.eventPublisher = eventPublisher;
        this.auditService = auditService;
    }

    /**
     * Create a new product.
     */
    public Product createProduct(Product product, String correlationId) {
        logger.info("[{}] Creating product: {}", correlationId, product.getSku());

        // Validate SKU uniqueness
        if (productRepository.findBySku(product.getSku()).isPresent()) {
            throw new IllegalArgumentException("Product SKU already exists: " + product.getSku());
        }

        // Validate ISBN uniqueness if provided
        if (product.getIsbn() != null && productRepository.findByIsbn(product.getIsbn()).isPresent()) {
            throw new IllegalArgumentException("Product ISBN already exists: " + product.getIsbn());
        }

        if (product.getIsbn13() != null && productRepository.findByIsbn13(product.getIsbn13()).isPresent()) {
            throw new IllegalArgumentException("Product ISBN13 already exists: " + product.getIsbn13());
        }

        Product savedProduct = productRepository.save(product);

        auditService.logProductCreated(savedProduct.getId(), product.getSku(), correlationId);

        // Publish event
        eventPublisher.publishProductCreatedEvent(savedProduct, correlationId);

        logger.info("[{}] Created product: {} with ID: {}", correlationId, product.getSku(), savedProduct.getId());
        return savedProduct;
    }

    /**
     * Update an existing product.
     */
    public Product updateProduct(UUID productId, Product updatedProduct, String correlationId) {
        logger.info("[{}] Updating product: {}", correlationId, productId);

        Product existingProduct = productRepository.findById(productId)
            .orElseThrow(() -> new IllegalArgumentException("Product not found: " + productId));

        // Update fields
        existingProduct.setTitle(updatedProduct.getTitle());
        existingProduct.setSubtitle(updatedProduct.getSubtitle());
        existingProduct.setDescription(updatedProduct.getDescription());
        existingProduct.setAuthors(updatedProduct.getAuthors());
        existingProduct.setPublisher(updatedProduct.getPublisher());
        existingProduct.setPublicationDate(updatedProduct.getPublicationDate());
        existingProduct.setEdition(updatedProduct.getEdition());
        existingProduct.setLanguage(updatedProduct.getLanguage());
        existingProduct.setPages(updatedProduct.getPages());
        existingProduct.setDimensions(updatedProduct.getDimensions());
        existingProduct.setWeight(updatedProduct.getWeight());
        existingProduct.setFormat(updatedProduct.getFormat());
        existingProduct.setCategory(updatedProduct.getCategory());
        existingProduct.setTags(updatedProduct.getTags());
        existingProduct.setImages(updatedProduct.getImages());
        existingProduct.setFeaturedImageUrl(updatedProduct.getFeaturedImageUrl());
        existingProduct.setIsActive(updatedProduct.getIsActive());
        existingProduct.setIsFeatured(updatedProduct.getIsFeatured());

        Product savedProduct = productRepository.save(existingProduct);

        auditService.logProductUpdated(productId, correlationId);

        // Publish event
        eventPublisher.publishProductUpdatedEvent(savedProduct, correlationId);

        logger.info("[{}] Updated product: {}", correlationId, productId);
        return savedProduct;
    }

    /**
     * Get product by ID.
     */
    public Optional<Product> getProductById(UUID productId, String correlationId) {
        logger.debug("[{}] Getting product: {}", correlationId, productId);
        return productRepository.findById(productId);
    }

    /**
     * Get product by SKU.
     */
    public Optional<Product> getProductBySku(String sku, String correlationId) {
        logger.debug("[{}] Getting product by SKU: {}", correlationId, sku);
        return productRepository.findBySku(sku);
    }

    /**
     * Get products by category.
     */
    public Page<Product> getProductsByCategory(UUID categoryId, Pageable pageable, String correlationId) {
        logger.debug("[{}] Getting products by category: {} (page: {})", correlationId, categoryId, pageable.getPageNumber());
        return productRepository.findByCategoryIdAndIsActive(categoryId, true, pageable);
    }

    /**
     * Get active products.
     */
    public Page<Product> getActiveProducts(Pageable pageable, String correlationId) {
        logger.debug("[{}] Getting active products (page: {})", correlationId, pageable.getPageNumber());
        return productRepository.findByIsActive(true, pageable);
    }

    /**
     * Get featured products.
     */
    public Page<Product> getFeaturedProducts(Pageable pageable, String correlationId) {
        logger.debug("[{}] Getting featured products (page: {})", correlationId, pageable.getPageNumber());
        return productRepository.findByIsFeaturedAndIsActive(true, true, pageable);
    }

    /**
     * Search products.
     */
    public Page<Product> searchProducts(String query, Pageable pageable, String correlationId) {
        logger.debug("[{}] Searching products: '{}' (page: {})", correlationId, query, pageable.getPageNumber());

        if (query == null || query.trim().isEmpty()) {
            return getActiveProducts(pageable, correlationId);
        }

        // Use full-text search for better results
        return productRepository.fullTextSearch(query, pageable);
    }

    /**
     * Advanced search with multiple filters.
     */
    public Page<Product> advancedSearch(UUID categoryId, BigDecimal minPrice, BigDecimal maxPrice,
                                      String author, String publisher, Boolean inStock,
                                      Pageable pageable, String correlationId) {
        logger.debug("[{}] Advanced search - category: {}, price: {}-{}, author: {}, publisher: {}, inStock: {} (page: {})",
                    correlationId, categoryId, minPrice, maxPrice, author, publisher, inStock, pageable.getPageNumber());

        return productRepository.advancedSearch(categoryId, minPrice, maxPrice, author, publisher, inStock, pageable);
    }

    /**
     * Get low stock products.
     */
    public Page<Product> getLowStockProducts(Pageable pageable, String correlationId) {
        logger.debug("[{}] Getting low stock products (page: {})", correlationId, pageable.getPageNumber());
        return productRepository.findLowStockProducts(pageable);
    }

    /**
     * Get out of stock products.
     */
    public Page<Product> getOutOfStockProducts(Pageable pageable, String correlationId) {
        logger.debug("[{}] Getting out of stock products (page: {})", correlationId, pageable.getPageNumber());
        return productRepository.findOutOfStockProducts(pageable);
    }

    /**
     * Delete product.
     */
    public void deleteProduct(UUID productId, String correlationId) {
        logger.info("[{}] Deleting product: {}", correlationId, productId);

        Product product = productRepository.findById(productId)
            .orElseThrow(() -> new IllegalArgumentException("Product not found: " + productId));

        productRepository.deleteById(productId);

        auditService.logProductDeleted(productId, product.getSku(), correlationId);

        // Publish event
        eventPublisher.publishProductDeletedEvent(product, correlationId);

        logger.info("[{}] Deleted product: {}", correlationId, productId);
    }

    /**
     * Add variant to product.
     */
    public ProductVariant addProductVariant(UUID productId, ProductVariant variant, String correlationId) {
        logger.info("[{}] Adding variant to product: {}", correlationId, productId);

        Product product = productRepository.findById(productId)
            .orElseThrow(() -> new IllegalArgumentException("Product not found: " + productId));

        // Validate SKU uniqueness
        if (variantRepository.findBySku(variant.getSku()).isPresent()) {
            throw new IllegalArgumentException("Variant SKU already exists: " + variant.getSku());
        }

        variant.setProduct(product);
        ProductVariant savedVariant = variantRepository.save(variant);

        auditService.logVariantCreated(savedVariant.getId(), variant.getSku(), correlationId);

        // Publish event
        eventPublisher.publishVariantCreatedEvent(savedVariant, correlationId);

        logger.info("[{}] Added variant: {} to product: {}", correlationId, savedVariant.getId(), productId);
        return savedVariant;
    }

    /**
     * Update product variant.
     */
    public ProductVariant updateProductVariant(UUID variantId, ProductVariant updatedVariant, String correlationId) {
        logger.info("[{}] Updating variant: {}", correlationId, variantId);

        ProductVariant existingVariant = variantRepository.findById(variantId)
            .orElseThrow(() -> new IllegalArgumentException("Variant not found: " + variantId));

        // Update fields
        existingVariant.setVariantType(updatedVariant.getVariantType());
        existingVariant.setVariantValue(updatedVariant.getVariantValue());
        existingVariant.setPrice(updatedVariant.getPrice());
        existingVariant.setCompareAtPrice(updatedVariant.getCompareAtPrice());
        existingVariant.setCostPrice(updatedVariant.getCostPrice());
        existingVariant.setInventoryQuantity(updatedVariant.getInventoryQuantity());
        existingVariant.setLowStockThreshold(updatedVariant.getLowStockThreshold());
        existingVariant.setIsAvailable(updatedVariant.getIsAvailable());
        existingVariant.setRequiresShipping(updatedVariant.getRequiresShipping());
        existingVariant.setWeight(updatedVariant.getWeight());
        existingVariant.setDimensions(updatedVariant.getDimensions());

        ProductVariant savedVariant = variantRepository.save(existingVariant);

        auditService.logVariantUpdated(variantId, correlationId);

        // Publish event
        eventPublisher.publishVariantUpdatedEvent(savedVariant, correlationId);

        logger.info("[{}] Updated variant: {}", correlationId, variantId);
        return savedVariant;
    }

    /**
     * Update inventory for a variant.
     */
    public void updateInventory(UUID variantId, int quantityChange, String reason, String correlationId) {
        logger.info("[{}] Updating inventory for variant: {} by {}", correlationId, variantId, quantityChange);

        ProductVariant variant = variantRepository.findById(variantId)
            .orElseThrow(() -> new IllegalArgumentException("Variant not found: " + variantId));

        int previousQuantity = variant.getInventoryQuantity();
        int newQuantity = previousQuantity + quantityChange;

        if (newQuantity < 0) {
            throw new IllegalArgumentException("Inventory cannot be negative");
        }

        variant.setInventoryQuantity(newQuantity);
        variantRepository.save(variant);

        auditService.logInventoryUpdated(variantId, previousQuantity, newQuantity, reason, correlationId);

        // Publish inventory event
        eventPublisher.publishInventoryUpdatedEvent(variant, previousQuantity, newQuantity, correlationId);

        logger.info("[{}] Updated inventory for variant: {} from {} to {}", correlationId, variantId, previousQuantity, newQuantity);
    }

    /**
     * Get product analytics.
     */
    public ProductAnalytics getProductAnalytics(String correlationId) {
        logger.debug("[{}] Getting product analytics", correlationId);

        long totalProducts = productRepository.countByIsActive(true);
        long featuredProducts = productRepository.countByIsFeaturedAndIsActive(true, true);
        long productsWithReviews = productRepository.countProductsWithReviews();

        List<ProductVariant> lowStockVariants = variantRepository.findLowStockVariants();
        List<ProductVariant> outOfStockVariants = variantRepository.findOutOfStockVariants();

        BigDecimal totalInventoryValue = variantRepository.getTotalInventoryValue();

        return new ProductAnalytics(totalProducts, featuredProducts, productsWithReviews,
                                  lowStockVariants.size(), outOfStockVariants.size(), totalInventoryValue);
    }

    /**
     * Result classes
     */
    public static class ProductAnalytics {
        private final long totalProducts;
        private final long featuredProducts;
        private final long productsWithReviews;
        private final long lowStockVariants;
        private final long outOfStockVariants;
        private final BigDecimal totalInventoryValue;

        public ProductAnalytics(long totalProducts, long featuredProducts, long productsWithReviews,
                              long lowStockVariants, long outOfStockVariants, BigDecimal totalInventoryValue) {
            this.totalProducts = totalProducts;
            this.featuredProducts = featuredProducts;
            this.productsWithReviews = productsWithReviews;
            this.lowStockVariants = lowStockVariants;
            this.outOfStockVariants = outOfStockVariants;
            this.totalInventoryValue = totalInventoryValue;
        }

        // Getters
        public long getTotalProducts() { return totalProducts; }
        public long getFeaturedProducts() { return featuredProducts; }
        public long getProductsWithReviews() { return productsWithReviews; }
        public long getLowStockVariants() { return lowStockVariants; }
        public long getOutOfStockVariants() { return outOfStockVariants; }
        public BigDecimal getTotalInventoryValue() { return totalInventoryValue; }
    }
}