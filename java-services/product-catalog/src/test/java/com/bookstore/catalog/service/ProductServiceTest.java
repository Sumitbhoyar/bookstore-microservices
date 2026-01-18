package com.bookstore.catalog.service;

import com.bookstore.catalog.domain.Product;
import com.bookstore.catalog.domain.ProductVariant;
import com.bookstore.catalog.repository.ProductRepository;
import com.bookstore.catalog.repository.ProductVariantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductVariantRepository variantRepository;

    @Mock
    private EventPublisherService eventPublisher;

    @Mock
    private AuditService auditService;

    @InjectMocks
    private ProductService productService;

    private Product testProduct;
    private ProductVariant testVariant;
    private UUID productId;
    private UUID variantId;

    @BeforeEach
    void setUp() {
        productId = UUID.randomUUID();
        variantId = UUID.randomUUID();
        testProduct = createTestProduct();
        testVariant = createTestVariant();
    }

    private Product createTestProduct() {
        Product product = new Product("SKU001", "Test Book");
        product.setId(productId);
        product.setDescription("Test book description");
        product.setIsbn("1234567890");
        product.setIsbn13("1234567890123");
        product.setAuthors(new String[]{"Test Author"});
        product.setPublisher("Test Publisher");
        product.setPages(300);
        product.setFormat("Paperback");
        product.setIsActive(true);
        product.setIsFeatured(false);
        return product;
    }

    private ProductVariant createTestVariant() {
        ProductVariant variant = new ProductVariant(testProduct, "VAR001", "format", "Paperback",
                                                  BigDecimal.valueOf(29.99));
        variant.setId(variantId);
        variant.setInventoryQuantity(100);
        variant.setLowStockThreshold(10);
        variant.setIsAvailable(true);
        variant.setRequiresShipping(true);
        return variant;
    }

    @Test
    void testCreateProduct_Success() {
        // Arrange
        String correlationId = "test-correlation-id";

        when(productRepository.findBySku("SKU001")).thenReturn(Optional.empty());
        when(productRepository.save(any(Product.class))).thenReturn(testProduct);

        // Act
        Product result = productService.createProduct(testProduct, correlationId);

        // Assert
        assertNotNull(result);
        assertEquals(testProduct, result);

        verify(productRepository).save(testProduct);
        verify(eventPublisher).publishProductCreatedEvent(testProduct, correlationId);
        verify(auditService).logProductCreated(productId, "SKU001", correlationId);
    }

    @Test
    void testCreateProduct_SkuExists() {
        // Arrange
        String correlationId = "test-correlation-id";

        when(productRepository.findBySku("SKU001")).thenReturn(Optional.of(testProduct));

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> productService.createProduct(testProduct, correlationId));

        assertEquals("Product SKU already exists: SKU001", exception.getMessage());
    }

    @Test
    void testGetProductById() {
        // Arrange
        String correlationId = "test-correlation-id";

        when(productRepository.findById(productId)).thenReturn(Optional.of(testProduct));

        // Act
        Optional<Product> result = productService.getProductById(productId, correlationId);

        // Assert
        assertTrue(result.isPresent());
        assertEquals(testProduct, result.get());
    }

    @Test
    void testGetProductBySku() {
        // Arrange
        String correlationId = "test-correlation-id";

        when(productRepository.findBySku("SKU001")).thenReturn(Optional.of(testProduct));

        // Act
        Optional<Product> result = productService.getProductBySku("SKU001", correlationId);

        // Assert
        assertTrue(result.isPresent());
        assertEquals(testProduct, result.get());
    }

    @Test
    void testUpdateProduct_Success() {
        // Arrange
        String correlationId = "test-correlation-id";
        Product updatedProduct = createTestProduct();
        updatedProduct.setTitle("Updated Title");

        when(productRepository.findById(productId)).thenReturn(Optional.of(testProduct));
        when(productRepository.save(any(Product.class))).thenReturn(updatedProduct);

        // Act
        Product result = productService.updateProduct(productId, updatedProduct, correlationId);

        // Assert
        assertNotNull(result);
        assertEquals("Updated Title", result.getTitle());

        verify(productRepository).save(testProduct);
        verify(eventPublisher).publishProductUpdatedEvent(updatedProduct, correlationId);
        verify(auditService).logProductUpdated(productId, correlationId);
    }

    @Test
    void testUpdateProduct_NotFound() {
        // Arrange
        String correlationId = "test-correlation-id";
        Product updatedProduct = createTestProduct();

        when(productRepository.findById(productId)).thenReturn(Optional.empty());

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> productService.updateProduct(productId, updatedProduct, correlationId));

        assertEquals("Product not found: " + productId, exception.getMessage());
    }

    @Test
    void testSearchProducts() {
        // Arrange
        String query = "test book";
        Pageable pageable = PageRequest.of(0, 10);
        String correlationId = "test-correlation-id";

        Page<Product> expectedPage = new PageImpl<>(Arrays.asList(testProduct), pageable, 1);
        when(productRepository.fullTextSearch(query, pageable)).thenReturn(expectedPage);

        // Act
        Page<Product> result = productService.searchProducts(query, pageable, correlationId);

        // Assert
        assertEquals(expectedPage, result);
        verify(productRepository).fullTextSearch(query, pageable);
    }

    @Test
    void testGetActiveProducts() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 10);
        String correlationId = "test-correlation-id";

        Page<Product> expectedPage = new PageImpl<>(Arrays.asList(testProduct), pageable, 1);
        when(productRepository.findByIsActive(true, pageable)).thenReturn(expectedPage);

        // Act
        Page<Product> result = productService.getActiveProducts(pageable, correlationId);

        // Assert
        assertEquals(expectedPage, result);
        verify(productRepository).findByIsActive(true, pageable);
    }

    @Test
    void testGetFeaturedProducts() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 10);
        String correlationId = "test-correlation-id";

        Page<Product> expectedPage = new PageImpl<>(Arrays.asList(testProduct), pageable, 1);
        when(productRepository.findByIsFeaturedAndIsActive(true, true, pageable)).thenReturn(expectedPage);

        // Act
        Page<Product> result = productService.getFeaturedProducts(pageable, correlationId);

        // Assert
        assertEquals(expectedPage, result);
        verify(productRepository).findByIsFeaturedAndIsActive(true, true, pageable);
    }

    @Test
    void testGetProductsByCategory() {
        // Arrange
        UUID categoryId = UUID.randomUUID();
        Pageable pageable = PageRequest.of(0, 10);
        String correlationId = "test-correlation-id";

        Page<Product> expectedPage = new PageImpl<>(Arrays.asList(testProduct), pageable, 1);
        when(productRepository.findByCategoryIdAndIsActive(categoryId, true, pageable)).thenReturn(expectedPage);

        // Act
        Page<Product> result = productService.getProductsByCategory(categoryId, pageable, correlationId);

        // Assert
        assertEquals(expectedPage, result);
        verify(productRepository).findByCategoryIdAndIsActive(categoryId, true, pageable);
    }

    @Test
    void testAddProductVariant_Success() {
        // Arrange
        String correlationId = "test-correlation-id";

        when(productRepository.findById(productId)).thenReturn(Optional.of(testProduct));
        when(variantRepository.findBySku("VAR001")).thenReturn(Optional.empty());
        when(variantRepository.save(any(ProductVariant.class))).thenReturn(testVariant);

        // Act
        ProductVariant result = productService.addProductVariant(productId, testVariant, correlationId);

        // Assert
        assertNotNull(result);
        assertEquals(testVariant, result);

        verify(variantRepository).save(testVariant);
        verify(eventPublisher).publishVariantCreatedEvent(testVariant, correlationId);
        verify(auditService).logVariantCreated(variantId, "VAR001", correlationId);
    }

    @Test
    void testUpdateInventory() {
        // Arrange
        int quantityChange = 50;
        String reason = "Stock replenishment";
        String correlationId = "test-correlation-id";

        when(variantRepository.findById(variantId)).thenReturn(Optional.of(testVariant));
        when(variantRepository.save(any(ProductVariant.class))).thenReturn(testVariant);

        // Act
        productService.updateInventory(variantId, quantityChange, reason, correlationId);

        // Assert
        verify(variantRepository).save(testVariant);
        verify(eventPublisher).publishInventoryUpdatedEvent(eq(testVariant), eq(100), eq(150), eq(correlationId));
        verify(auditService).logInventoryUpdated(variantId, 100, 150, reason, correlationId);
    }

    @Test
    void testGetLowStockProducts() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 10);
        String correlationId = "test-correlation-id";

        Page<Product> expectedPage = new PageImpl<>(Arrays.asList(testProduct), pageable, 1);
        when(productRepository.findLowStockProducts(pageable)).thenReturn(expectedPage);

        // Act
        Page<Product> result = productService.getLowStockProducts(pageable, correlationId);

        // Assert
        assertEquals(expectedPage, result);
        verify(productRepository).findLowStockProducts(pageable);
    }

    @Test
    void testGetOutOfStockProducts() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 10);
        String correlationId = "test-correlation-id";

        Page<Product> expectedPage = new PageImpl<>(Arrays.asList(testProduct), pageable, 1);
        when(productRepository.findOutOfStockProducts(pageable)).thenReturn(expectedPage);

        // Act
        Page<Product> result = productService.getOutOfStockProducts(pageable, correlationId);

        // Assert
        assertEquals(expectedPage, result);
        verify(productRepository).findOutOfStockProducts(pageable);
    }

    @Test
    void testGetProductAnalytics() {
        // Arrange
        String correlationId = "test-correlation-id";

        when(productRepository.countByIsActive(true)).thenReturn(100L);
        when(productRepository.countByIsFeaturedAndIsActive(true, true)).thenReturn(20L);
        when(productRepository.countProductsWithReviews()).thenReturn(50L);
        when(variantRepository.findLowStockVariants()).thenReturn(Arrays.asList(testVariant));
        when(variantRepository.findOutOfStockVariants()).thenReturn(Arrays.asList());
        when(variantRepository.getTotalInventoryValue()).thenReturn(BigDecimal.valueOf(5000.00));

        // Act
        ProductService.ProductAnalytics analytics = productService.getProductAnalytics(correlationId);

        // Assert
        assertEquals(100L, analytics.getTotalProducts());
        assertEquals(20L, analytics.getFeaturedProducts());
        assertEquals(50L, analytics.getProductsWithReviews());
        assertEquals(1L, analytics.getLowStockVariants());
        assertEquals(0L, analytics.getOutOfStockVariants());
        assertEquals(BigDecimal.valueOf(5000.00), analytics.getTotalInventoryValue());
    }

    @Test
    void testDeleteProduct_Success() {
        // Arrange
        String correlationId = "test-correlation-id";

        when(productRepository.findById(productId)).thenReturn(Optional.of(testProduct));

        // Act
        productService.deleteProduct(productId, correlationId);

        // Assert
        verify(productRepository).deleteById(productId);
        verify(eventPublisher).publishProductDeletedEvent(testProduct, correlationId);
        verify(auditService).logProductDeleted(productId, "SKU001", correlationId);
    }

    @Test
    void testDeleteProduct_NotFound() {
        // Arrange
        String correlationId = "test-correlation-id";

        when(productRepository.findById(productId)).thenReturn(Optional.empty());

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> productService.deleteProduct(productId, correlationId));

        assertEquals("Product not found: " + productId, exception.getMessage());
    }
}