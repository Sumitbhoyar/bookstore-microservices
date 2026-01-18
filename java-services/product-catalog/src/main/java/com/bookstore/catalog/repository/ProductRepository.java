package com.bookstore.catalog.repository;

import com.bookstore.catalog.domain.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for Product entity operations.
 */
@Repository
public interface ProductRepository extends JpaRepository<Product, UUID> {

    /**
     * Find product by SKU.
     */
    Optional<Product> findBySku(String sku);

    /**
     * Find product by ISBN.
     */
    Optional<Product> findByIsbn(String isbn);

    /**
     * Find product by ISBN13.
     */
    Optional<Product> findByIsbn13(String isbn13);

    /**
     * Find products by category.
     */
    Page<Product> findByCategoryIdAndIsActive(UUID categoryId, boolean isActive, Pageable pageable);

    /**
     * Find active products.
     */
    Page<Product> findByIsActive(boolean isActive, Pageable pageable);

    /**
     * Find featured products.
     */
    Page<Product> findByIsFeaturedAndIsActive(boolean isFeatured, boolean isActive, Pageable pageable);

    /**
     * Find products by author.
     */
    @Query("SELECT p FROM Product p WHERE :author = ANY(p.authors) AND p.isActive = true")
    Page<Product> findByAuthorAndActive(@Param("author") String author, Pageable pageable);

    /**
     * Find products by tag.
     */
    @Query("SELECT p FROM Product p WHERE :tag = ANY(p.tags) AND p.isActive = true")
    Page<Product> findByTagAndActive(@Param("tag") String tag, Pageable pageable);

    /**
     * Search products by title or description.
     */
    @Query("""
        SELECT p FROM Product p WHERE p.isActive = true AND (
            LOWER(p.title) LIKE LOWER(CONCAT('%', :query, '%')) OR
            LOWER(p.description) LIKE LOWER(CONCAT('%', :query, '%')) OR
            LOWER(p.subtitle) LIKE LOWER(CONCAT('%', :query, '%'))
        )
        """)
    Page<Product> searchByTitleOrDescription(@Param("query") String query, Pageable pageable);

    /**
     * Search products by author name.
     */
    @Query("""
        SELECT p FROM Product p WHERE p.isActive = true AND
        EXISTS (SELECT 1 FROM unnest(p.authors) AS author WHERE LOWER(author) LIKE LOWER(CONCAT('%', :author, '%')))
        """)
    Page<Product> searchByAuthor(@Param("author") String author, Pageable pageable);

    /**
     * Find products with low stock variants.
     */
    @Query("""
        SELECT DISTINCT p FROM Product p
        JOIN p.variants v
        WHERE p.isActive = true AND v.isAvailable = true
        AND v.inventoryQuantity <= v.lowStockThreshold
        AND v.inventoryQuantity > 0
        """)
    Page<Product> findLowStockProducts(Pageable pageable);

    /**
     * Find products that are out of stock.
     */
    @Query("""
        SELECT DISTINCT p FROM Product p
        JOIN p.variants v
        WHERE p.isActive = true
        AND (v.isAvailable = false OR v.inventoryQuantity <= 0)
        AND NOT EXISTS (
            SELECT 1 FROM ProductVariant v2
            WHERE v2.product = p AND v2.isAvailable = true AND v2.inventoryQuantity > 0
        )
        """)
    Page<Product> findOutOfStockProducts(Pageable pageable);

    /**
     * Find products by publication year.
     */
    @Query("SELECT p FROM Product p WHERE EXTRACT(YEAR FROM p.publicationDate) = :year AND p.isActive = true")
    Page<Product> findByPublicationYear(@Param("year") int year, Pageable pageable);

    /**
     * Find products by publisher.
     */
    Page<Product> findByPublisherAndIsActive(String publisher, boolean isActive, Pageable pageable);

    /**
     * Get product count by category.
     */
    @Query("SELECT COUNT(p) FROM Product p WHERE p.category.id = :categoryId AND p.isActive = true")
    long countByCategoryIdAndActive(@Param("categoryId") UUID categoryId);

    /**
     * Get featured products count.
     */
    long countByIsFeaturedAndIsActive(boolean isFeatured, boolean isActive);

    /**
     * Count products by active status.
     */
    long countByIsActive(boolean isActive);

    /**
     * Get products with reviews count.
     */
    @Query("SELECT COUNT(DISTINCT p) FROM Product p JOIN p.reviews r WHERE p.isActive = true")
    long countProductsWithReviews();

    /**
     * Full-text search using PostgreSQL full-text search.
     */
    @Query(value = """
        SELECT * FROM products p
        WHERE p.is_active = true
        AND to_tsvector('english', p.title || ' ' || COALESCE(p.subtitle, '') || ' ' || COALESCE(p.description, ''))
        @@ plainto_tsquery('english', :query)
        ORDER BY ts_rank(to_tsvector('english', p.title || ' ' || COALESCE(p.subtitle, '') || ' ' || COALESCE(p.description, '')), plainto_tsquery('english', :query)) DESC
        """,
        countQuery = """
        SELECT COUNT(*) FROM products p
        WHERE p.is_active = true
        AND to_tsvector('english', p.title || ' ' || COALESCE(p.subtitle, '') || ' ' || COALESCE(p.description, ''))
        @@ plainto_tsquery('english', :query)
        """,
        nativeQuery = true)
    Page<Product> fullTextSearch(@Param("query") String query, Pageable pageable);

    /**
     * Advanced search with multiple filters.
     */
    @Query("""
        SELECT DISTINCT p FROM Product p
        LEFT JOIN p.variants v
        WHERE p.isActive = true
        AND (:categoryId IS NULL OR p.category.id = :categoryId)
        AND (:minPrice IS NULL OR v.price >= :minPrice)
        AND (:maxPrice IS NULL OR v.price <= :maxPrice)
        AND (:author IS NULL OR :author = ANY(p.authors))
        AND (:publisher IS NULL OR p.publisher = :publisher)
        AND (:inStock IS NULL OR (:inStock = true AND v.inventoryQuantity > 0) OR (:inStock = false))
        """)
    Page<Product> advancedSearch(@Param("categoryId") UUID categoryId,
                                @Param("minPrice") java.math.BigDecimal minPrice,
                                @Param("maxPrice") java.math.BigDecimal maxPrice,
                                @Param("author") String author,
                                @Param("publisher") String publisher,
                                @Param("inStock") Boolean inStock,
                                Pageable pageable);
}