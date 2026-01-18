package com.bookstore.catalog.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Product entity representing a book or product in the catalog.
 */
@Entity
@Table(name = "products", indexes = {
    @Index(name = "idx_products_category_id", columnList = "category_id"),
    @Index(name = "idx_products_is_active", columnList = "is_active"),
    @Index(name = "idx_products_is_featured", columnList = "is_featured"),
    @Index(name = "idx_products_created_at", columnList = "created_at")
})
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotBlank
    @Size(max = 50)
    @Column(unique = true, nullable = false)
    private String sku;

    @NotBlank
    @Size(max = 500)
    @Column(nullable = false)
    private String title;

    @Size(max = 500)
    private String subtitle;

    @Column(length = 2000)
    private String description;

    @Size(max = 20)
    @Column(unique = true)
    private String isbn;

    @Size(max = 20)
    @Column(unique = true)
    private String isbn13;

    @Column(columnDefinition = "TEXT[]")
    private String[] authors;

    @Size(max = 255)
    private String publisher;

    private LocalDate publicationDate;

    @Size(max = 50)
    private String edition;

    @NotBlank
    @Size(max = 10)
    @Column(nullable = false)
    private String language = "en";

    private Integer pages;

    @Size(max = 100)
    private String dimensions;

    @Column(precision = 8, scale = 2)
    private Double weight;

    @Size(max = 50)
    private String format;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @Column(columnDefinition = "TEXT[]")
    private String[] tags;

    @Column(columnDefinition = "TEXT[]")
    private String[] images;

    private String featuredImageUrl;

    @Column(nullable = false)
    private Boolean isActive = true;

    @Column(nullable = false)
    private Boolean isFeatured = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // Relationships
    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProductVariant> variants = new ArrayList<>();

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProductReview> reviews = new ArrayList<>();

    public Product() {}

    public Product(String sku, String title) {
        this.sku = sku;
        this.title = title;
    }

    // Getters and setters
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getSku() {
        return sku;
    }

    public void setSku(String sku) {
        this.sku = sku;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSubtitle() {
        return subtitle;
    }

    public void setSubtitle(String subtitle) {
        this.subtitle = subtitle;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getIsbn() {
        return isbn;
    }

    public void setIsbn(String isbn) {
        this.isbn = isbn;
    }

    public String getIsbn13() {
        return isbn13;
    }

    public void setIsbn13(String isbn13) {
        this.isbn13 = isbn13;
    }

    public String[] getAuthors() {
        return authors;
    }

    public void setAuthors(String[] authors) {
        this.authors = authors;
    }

    public String getPublisher() {
        return publisher;
    }

    public void setPublisher(String publisher) {
        this.publisher = publisher;
    }

    public LocalDate getPublicationDate() {
        return publicationDate;
    }

    public void setPublicationDate(LocalDate publicationDate) {
        this.publicationDate = publicationDate;
    }

    public String getEdition() {
        return edition;
    }

    public void setEdition(String edition) {
        this.edition = edition;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public Integer getPages() {
        return pages;
    }

    public void setPages(Integer pages) {
        this.pages = pages;
    }

    public String getDimensions() {
        return dimensions;
    }

    public void setDimensions(String dimensions) {
        this.dimensions = dimensions;
    }

    public Double getWeight() {
        return weight;
    }

    public void setWeight(Double weight) {
        this.weight = weight;
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public Category getCategory() {
        return category;
    }

    public void setCategory(Category category) {
        this.category = category;
    }

    public String[] getTags() {
        return tags;
    }

    public void setTags(String[] tags) {
        this.tags = tags;
    }

    public String[] getImages() {
        return images;
    }

    public void setImages(String[] images) {
        this.images = images;
    }

    public String getFeaturedImageUrl() {
        return featuredImageUrl;
    }

    public void setFeaturedImageUrl(String featuredImageUrl) {
        this.featuredImageUrl = featuredImageUrl;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
    }

    public Boolean getIsFeatured() {
        return isFeatured;
    }

    public void setIsFeatured(Boolean isFeatured) {
        this.isFeatured = isFeatured;
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

    public List<ProductVariant> getVariants() {
        return variants;
    }

    public void setVariants(List<ProductVariant> variants) {
        this.variants = variants;
    }

    public List<ProductReview> getReviews() {
        return reviews;
    }

    public void setReviews(List<ProductReview> reviews) {
        this.reviews = reviews;
    }

    // Business methods
    public String getDisplayTitle() {
        if (subtitle != null && !subtitle.isEmpty()) {
            return title + ": " + subtitle;
        }
        return title;
    }

    public String getPrimaryAuthor() {
        if (authors != null && authors.length > 0) {
            return authors[0];
        }
        return null;
    }

    public String getAllAuthors() {
        if (authors == null || authors.length == 0) {
            return "";
        }
        return String.join(", ", authors);
    }

    public boolean hasVariants() {
        return variants != null && !variants.isEmpty();
    }

    public ProductVariant getDefaultVariant() {
        if (variants == null || variants.isEmpty()) {
            return null;
        }
        return variants.stream()
            .filter(v -> Boolean.TRUE.equals(v.getIsAvailable()))
            .findFirst()
            .orElse(variants.get(0));
    }

    public double getAverageRating() {
        if (reviews == null || reviews.isEmpty()) {
            return 0.0;
        }
        return reviews.stream()
            .mapToInt(ProductReview::getRating)
            .average()
            .orElse(0.0);
    }

    public int getReviewCount() {
        return reviews != null ? reviews.size() : 0;
    }

    public boolean isInStock() {
        if (hasVariants()) {
            return variants.stream().anyMatch(v ->
                Boolean.TRUE.equals(v.getIsAvailable()) &&
                v.getInventoryQuantity() != null &&
                v.getInventoryQuantity() > 0);
        }
        return false;
    }

    public boolean isLowStock() {
        if (hasVariants()) {
            return variants.stream().anyMatch(v ->
                Boolean.TRUE.equals(v.getIsAvailable()) &&
                v.getInventoryQuantity() != null &&
                v.getInventoryQuantity() <= v.getLowStockThreshold());
        }
        return false;
    }
}