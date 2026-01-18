package com.bookstore.catalog.repository;

import com.bookstore.catalog.domain.ProductVariant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for ProductVariant entity operations.
 */
@Repository
public interface ProductVariantRepository extends JpaRepository<ProductVariant, UUID> {

    /**
     * Find variant by SKU.
     */
    Optional<ProductVariant> findBySku(String sku);

    /**
     * Find all variants for a product.
     */
    List<ProductVariant> findByProductId(UUID productId);

    /**
     * Find available variants for a product.
     */
    List<ProductVariant> findByProductIdAndIsAvailable(UUID productId, boolean isAvailable);

    /**
     * Find variants by product and variant type.
     */
    List<ProductVariant> findByProductIdAndVariantType(UUID productId, String variantType);

    /**
     * Find variants with low stock.
     */
    @Query("SELECT v FROM ProductVariant v WHERE v.isAvailable = true AND v.inventoryQuantity <= v.lowStockThreshold AND v.inventoryQuantity > 0")
    List<ProductVariant> findLowStockVariants();

    /**
     * Find variants that are out of stock.
     */
    @Query("SELECT v FROM ProductVariant v WHERE v.isAvailable = false OR v.inventoryQuantity <= 0")
    List<ProductVariant> findOutOfStockVariants();

    /**
     * Find variants in price range.
     */
    List<ProductVariant> findByPriceBetween(BigDecimal minPrice, BigDecimal maxPrice);

    /**
     * Find variants by product with price sorting.
     */
    List<ProductVariant> findByProductIdOrderByPriceAsc(UUID productId);

    /**
     * Count variants for a product.
     */
    long countByProductId(UUID productId);

    /**
     * Count available variants for a product.
     */
    long countByProductIdAndIsAvailable(UUID productId, boolean isAvailable);

    /**
     * Check if product has available variants.
     */
    boolean existsByProductIdAndIsAvailableAndInventoryQuantityGreaterThan(UUID productId, boolean isAvailable, int quantity);

    /**
     * Update inventory quantity.
     */
    @Query("UPDATE ProductVariant v SET v.inventoryQuantity = v.inventoryQuantity + :quantity WHERE v.id = :variantId")
    int updateInventoryQuantity(@Param("variantId") UUID variantId, @Param("quantity") int quantity);

    /**
     * Set variant availability.
     */
    @Query("UPDATE ProductVariant v SET v.isAvailable = :available WHERE v.id = :variantId")
    int updateAvailability(@Param("variantId") UUID variantId, @Param("available") boolean available);

    /**
     * Update price.
     */
    @Query("UPDATE ProductVariant v SET v.price = :price WHERE v.id = :variantId")
    int updatePrice(@Param("variantId") UUID variantId, @Param("price") BigDecimal price);

    /**
     * Get total inventory value.
     */
    @Query("SELECT SUM(v.price * v.inventoryQuantity) FROM ProductVariant v WHERE v.isAvailable = true")
    BigDecimal getTotalInventoryValue();

    /**
     * Get average price by variant type.
     */
    @Query("SELECT AVG(v.price) FROM ProductVariant v WHERE v.variantType = :variantType AND v.isAvailable = true")
    BigDecimal getAveragePriceByType(@Param("variantType") String variantType);

    /**
     * Find variants that need reorder.
     */
    @Query("SELECT v FROM ProductVariant v WHERE v.isAvailable = true AND v.inventoryQuantity <= :threshold ORDER BY v.inventoryQuantity ASC")
    List<ProductVariant> findVariantsNeedingReorder(@Param("threshold") int threshold);

    /**
     * Get inventory summary for a product.
     */
    @Query("""
        SELECT
            SUM(v.inventoryQuantity) as totalQuantity,
            MIN(v.inventoryQuantity) as minQuantity,
            MAX(v.inventoryQuantity) as maxQuantity,
            AVG(v.price) as avgPrice,
            COUNT(v) as variantCount
        FROM ProductVariant v
        WHERE v.product.id = :productId AND v.isAvailable = true
        """)
    Object[] getInventorySummary(@Param("productId") UUID productId);
}