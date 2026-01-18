package com.bookstore.catalog.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Product Variant entity representing different formats/editions of a product.
 */
@Entity
@Table(name = "product_variants", indexes = {
    @Index(name = "idx_product_variants_product_id", columnList = "product_id"),
    @Index(name = "idx_product_variants_sku", columnList = "sku"),
    @Index(name = "idx_product_variants_available", columnList = "is_available"),
    @Index(name = "idx_product_variants_price", columnList = "price")
})
public class ProductVariant {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @NotBlank
    @Column(unique = true, nullable = false)
    private String sku;

    @NotBlank
    @Column(name = "variant_type", nullable = false)
    private String variantType;

    @NotBlank
    @Column(name = "variant_value", nullable = false)
    private String variantValue;

    @NotNull
    @DecimalMin(value = "0.0", inclusive = false)
    @Column(precision = 10, scale = 2, nullable = false)
    private BigDecimal price;

    @DecimalMin(value = "0.0")
    @Column(name = "compare_at_price", precision = 10, scale = 2)
    private BigDecimal compareAtPrice;

    @DecimalMin(value = "0.0")
    @Column(name = "cost_price", precision = 10, scale = 2)
    private BigDecimal costPrice;

    @NotNull
    @Min(0)
    @Column(name = "inventory_quantity", nullable = false)
    private Integer inventoryQuantity = 0;

    @NotNull
    @Min(0)
    @Column(name = "low_stock_threshold", nullable = false)
    private Integer lowStockThreshold = 10;

    @NotNull
    @Column(name = "is_available", nullable = false)
    private Boolean isAvailable = true;

    @NotNull
    @Column(name = "requires_shipping", nullable = false)
    private Boolean requiresShipping = true;

    @DecimalMin(value = "0.0")
    @Column(precision = 8, scale = 2)
    private BigDecimal weight;

    private String dimensions;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public ProductVariant() {}

    public ProductVariant(Product product, String sku, String variantType, String variantValue, BigDecimal price) {
        this.product = product;
        this.sku = sku;
        this.variantType = variantType;
        this.variantValue = variantValue;
        this.price = price;
    }

    // Getters and setters
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Product getProduct() {
        return product;
    }

    public void setProduct(Product product) {
        this.product = product;
    }

    public String getSku() {
        return sku;
    }

    public void setSku(String sku) {
        this.sku = sku;
    }

    public String getVariantType() {
        return variantType;
    }

    public void setVariantType(String variantType) {
        this.variantType = variantType;
    }

    public String getVariantValue() {
        return variantValue;
    }

    public void setVariantValue(String variantValue) {
        this.variantValue = variantValue;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public BigDecimal getCompareAtPrice() {
        return compareAtPrice;
    }

    public void setCompareAtPrice(BigDecimal compareAtPrice) {
        this.compareAtPrice = compareAtPrice;
    }

    public BigDecimal getCostPrice() {
        return costPrice;
    }

    public void setCostPrice(BigDecimal costPrice) {
        this.costPrice = costPrice;
    }

    public Integer getInventoryQuantity() {
        return inventoryQuantity;
    }

    public void setInventoryQuantity(Integer inventoryQuantity) {
        this.inventoryQuantity = inventoryQuantity;
    }

    public Integer getLowStockThreshold() {
        return lowStockThreshold;
    }

    public void setLowStockThreshold(Integer lowStockThreshold) {
        this.lowStockThreshold = lowStockThreshold;
    }

    public Boolean getIsAvailable() {
        return isAvailable;
    }

    public void setIsAvailable(Boolean isAvailable) {
        this.isAvailable = isAvailable;
    }

    public Boolean getRequiresShipping() {
        return requiresShipping;
    }

    public void setRequiresShipping(Boolean requiresShipping) {
        this.requiresShipping = requiresShipping;
    }

    public BigDecimal getWeight() {
        return weight;
    }

    public void setWeight(BigDecimal weight) {
        this.weight = weight;
    }

    public String getDimensions() {
        return dimensions;
    }

    public void setDimensions(String dimensions) {
        this.dimensions = dimensions;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    // Business methods
    public boolean isOnSale() {
        return compareAtPrice != null && compareAtPrice.compareTo(price) > 0;
    }

    public BigDecimal getDiscountAmount() {
        if (isOnSale()) {
            return compareAtPrice.subtract(price);
        }
        return BigDecimal.ZERO;
    }

    public BigDecimal getDiscountPercentage() {
        if (isOnSale()) {
            return compareAtPrice.subtract(price)
                .divide(compareAtPrice, 4, BigDecimal.ROUND_HALF_UP)
                .multiply(new BigDecimal("100"));
        }
        return BigDecimal.ZERO;
    }

    public boolean isInStock() {
        return isAvailable && inventoryQuantity != null && inventoryQuantity > 0;
    }

    public boolean isLowStock() {
        return isAvailable && inventoryQuantity != null &&
               inventoryQuantity <= lowStockThreshold && inventoryQuantity > 0;
    }

    public boolean isOutOfStock() {
        return !isAvailable || inventoryQuantity == null || inventoryQuantity <= 0;
    }

    public BigDecimal getProfitMargin() {
        if (costPrice != null && costPrice.compareTo(BigDecimal.ZERO) > 0) {
            return price.subtract(costPrice)
                .divide(price, 4, BigDecimal.ROUND_HALF_UP)
                .multiply(new BigDecimal("100"));
        }
        return BigDecimal.ZERO;
    }

    public String getVariantDisplayName() {
        if ("format".equalsIgnoreCase(variantType)) {
            return variantValue;
        } else if ("edition".equalsIgnoreCase(variantType)) {
            return product.getTitle() + " (" + variantValue + ")";
        } else {
            return product.getTitle() + " - " + variantType + ": " + variantValue;
        }
    }
}