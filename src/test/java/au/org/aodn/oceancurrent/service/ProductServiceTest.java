package au.org.aodn.oceancurrent.service;

import au.org.aodn.oceancurrent.configuration.product.ProductProperties;
import au.org.aodn.oceancurrent.model.Product;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private Map<String, Product> productMap;

    @Mock
    private Map<String, Product> productGroupMap;

    @Mock
    private Map<String, Product> allProductsMap;

    @Mock
    private ProductProperties productProperties;

    private ProductService productService;

    private Product mockSnapshotSst;
    private Product mockFourHourSst;
    private List<Product> mockProductHierarchy;

    @BeforeEach
    public void setup() {
        // Initialize the service with mocks
        productService = new ProductService(productMap, productGroupMap, allProductsMap, productProperties);

        // Create standalone product
        mockSnapshotSst = Product.builder()
                .title("Snapshot SST")
                .id("snapshotSst")
                .type(null)
                .build();

        // Create product group (first without children)
        mockFourHourSst = Product.builder()
                .title("Four hour SST")
                .id("fourHourSst")
                .type("ProductGroup")
                .children(new ArrayList<>())
                .build();

        // Create child product
        Product mockSstFilled = Product.builder()
                .title("SST Filled")
                .id("fourHourSst-sstFilled")
                .type(null)
                .parent(mockFourHourSst)
                .build();

        // Add the child to the product group
        mockFourHourSst.setChildren(Collections.singletonList(mockSstFilled));

        // Set up product hierarchy
        mockProductHierarchy = Arrays.asList(mockSnapshotSst, mockFourHourSst);
        }

    @Test
    public void testIsValidProductId() {
        // Given
        when(productMap.containsKey("snapshotSst")).thenReturn(true);
        when(productMap.containsKey("invalidId")).thenReturn(false);

        // When & Then
//        assertTrue(productService.isValidProductId("snapshotSst"));
//        assertFalse(productService.isValidProductId("invalidId"));
        assertThat(productService.isValidProductId("snapshotSst")).isTrue();
        assertThat(productService.isValidProductId("invalidId")).isFalse();
    }

    @Test
    public void testIsValidProductGroupId() {
        // Given
        when(productGroupMap.containsKey("fourHourSst")).thenReturn(true);
        when(productGroupMap.containsKey("snapshotSst")).thenReturn(false);

        // When & Then
        assertThat(productService.isValidProductGroupId("fourHourSst")).isTrue();
        assertThat(productService.isValidProductGroupId("snapshotSst")).isFalse();
    }

    @Test
    public void testIsValidId() {
        // Given
        when(allProductsMap.containsKey("snapshotSst")).thenReturn(true);
        when(allProductsMap.containsKey("fourHourSst")).thenReturn(true);
        when(allProductsMap.containsKey("invalidId")).thenReturn(false);

        // When & Then
        assertThat(productService.isValidId("snapshotSst")).isTrue();
        assertThat(productService.isValidId("fourHourSst")).isTrue();
        assertThat(productService.isValidId("invalidId")).isFalse();
    }

    @Test
    public void testGetProductById() {
        // Given
        when(productMap.get("snapshotSst")).thenReturn(mockSnapshotSst);
        when(productMap.get("invalidId")).thenReturn(null);

        // When & Then
        assertThat(productService.getProductById("snapshotSst"))
                .isPresent()
                .hasValueSatisfying(product -> {
                    assertThat(product.getId()).isEqualTo("snapshotSst");
                    assertThat(product.getTitle()).isEqualTo("Snapshot SST");
                });

        assertThat(productService.getProductById("invalidId")).isEmpty();
    }

    @Test
    public void testGetProductGroupById() {
        // Given
        when(productGroupMap.get("fourHourSst")).thenReturn(mockFourHourSst);
        when(productGroupMap.get("invalidId")).thenReturn(null);

        // When & Then
        assertThat(productService.getProductGroupById("fourHourSst"))
                .isPresent()
                .hasValueSatisfying(product -> {
                    assertThat(product.getId()).isEqualTo("fourHourSst");
                    assertThat(product.getTitle()).isEqualTo("Four hour SST");
                    assertThat(product.getType()).isEqualTo("ProductGroup");
                    assertThat(product.getChildren()).hasSize(1);
                });

        assertThat(productService.getProductGroupById("invalidId")).isEmpty();
    }

    @Test
    public void testGetById() {
        // Given
        when(allProductsMap.get("snapshotSst")).thenReturn(mockSnapshotSst);
        when(allProductsMap.get("fourHourSst")).thenReturn(mockFourHourSst);
        when(allProductsMap.get("invalidId")).thenReturn(null);

        // When & Then
        assertThat(productService.getById("snapshotSst"))
                .isPresent()
                .hasValueSatisfying(product -> {
                    assertThat(product.getId()).isEqualTo("snapshotSst");
                    assertThat(product.getTitle()).isEqualTo("Snapshot SST");
                });

        assertThat(productService.getById("fourHourSst"))
                .isPresent()
                .hasValueSatisfying(product -> {
                    assertThat(product.getId()).isEqualTo("fourHourSst");
                    assertThat(product.getTitle()).isEqualTo("Four hour SST");
                    assertThat(product.getType()).isEqualTo("ProductGroup");
                });

        assertThat(productService.getById("invalidId")).isEmpty();
    }

    @Test
    public void testGetLeafProducts() {
        // When
        Map<String, Product> result = productService.getLeafProducts();

        // Then
        assertThat(result).isSameAs(productMap);
    }

    @Test
    public void testGetProductGroups() {
        // When
        Map<String, Product> result = productService.getProductGroups();

        // Then
        assertThat(result).isSameAs(productGroupMap);
    }

    @Test
    public void testGetProductHierarchy() {
        // Given
        when(productProperties.getProducts()).thenReturn(mockProductHierarchy);

        // When
        List<Product> result = productService.getProductHierarchy();

        // Then
        assertThat(result).isSameAs(mockProductHierarchy);
        assertThat(result).hasSize(2);
        assertThat(result).containsExactly(mockSnapshotSst, mockFourHourSst);
    }
}
