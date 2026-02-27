package au.org.aodn.oceancurrent.controller;

import au.org.aodn.oceancurrent.model.Product;
import au.org.aodn.oceancurrent.service.ProductService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ProductController.class)
@AutoConfigureMockMvc(addFilters = false)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProductService productService;

    private List<Product> mockProductHierarchy;
    private Map<String, Product> mockLeafProducts;
    private Map<String, Product> mockAllProducts;

    @BeforeAll
    public void setup() {
        // Setup test data
        mockProductHierarchy = new ArrayList<>();
        mockLeafProducts = new HashMap<>();
        mockAllProducts = new HashMap<>();

        // Create standalone products
        Product mockSnapshotSst = Product.builder()
                .title("Snapshot SST")
                .id("snapshotSst")
                .type("Product")
                .build();

        Product mockSurfaceWaves = Product.builder()
                .title("Surface Waves")
                .id("surfaceWaves")
                .type("Product")
                .build();

        Product mockArgo = Product.builder()
                .title("Argo")
                .id("argo")
                .type("Product")
                .build();

        Product mockTidalCurrents = Product.builder()
                .title("Tidal Currents")
                .id("tidalCurrents")
                .type("Product")
                .build();

        Product mockEacMooringArray = Product.builder()
                .title("EAC Mooring Array")
                .id("EACMooringArray")
                .type("Product")
                .build();

        Product mockSealCtd = Product.builder()
                .title("SealCTD")
                .id("sealCtd")
                .type("ProductGroup")
                .children(new ArrayList<>())
                .build();

        // Add standalone products to leafProducts map
        mockLeafProducts.put(mockSnapshotSst.getId(), mockSnapshotSst);
        mockLeafProducts.put(mockSurfaceWaves.getId(), mockSurfaceWaves);
        mockLeafProducts.put(mockArgo.getId(), mockArgo);
        mockLeafProducts.put(mockTidalCurrents.getId(), mockTidalCurrents);
        mockLeafProducts.put(mockEacMooringArray.getId(), mockEacMooringArray);

        // Add standalone products to allProducts map
        mockAllProducts.put(mockSnapshotSst.getId(), mockSnapshotSst);
        mockAllProducts.put(mockSurfaceWaves.getId(), mockSurfaceWaves);
        mockAllProducts.put(mockArgo.getId(), mockArgo);
        mockAllProducts.put(mockTidalCurrents.getId(), mockTidalCurrents);
        mockAllProducts.put(mockEacMooringArray.getId(), mockEacMooringArray);

        // Add standalone products to product hierarchy
        mockProductHierarchy.add(mockSnapshotSst);
        mockProductHierarchy.add(mockSurfaceWaves);
        mockProductHierarchy.add(mockArgo);
        mockProductHierarchy.add(mockTidalCurrents);
        mockProductHierarchy.add(mockEacMooringArray);
        mockProductHierarchy.add(mockSealCtd);

        // Create product group with children
        Product mockFourHourSst = Product.builder()
                .title("Four hour SST")
                .id("fourHourSst")
                .type("ProductGroup")
                .children(new ArrayList<>())
                .build();

        // Create children for the fourHourSst group
        Product mockSstFilled = Product.builder()
                .title("SST Filled")
                .id("fourHourSst-sstFilled")
                .type("Product")
                .parent(mockFourHourSst)
                .build();

        Product mockSst = Product.builder()
                .title("SST")
                .id("fourHourSst-sst")
                .type("Product")
                .parent(mockFourHourSst)
                .build();

        Product mockSstAge = Product.builder()
                .title("SST Age")
                .id("fourHourSst-sstAge")
                .type("Product")
                .parent(mockFourHourSst)
                .build();

        Product mockWindSpeed = Product.builder()
                .title("Wind Speed")
                .id("fourHourSst-windSpeed")
                .type("Product")
                .parent(mockFourHourSst)
                .build();

        // Add children to the fourHourSst group
        mockFourHourSst.setChildren(Arrays.asList(mockSstFilled, mockSst, mockSstAge, mockWindSpeed));

        // Add product group to allProducts map
        mockAllProducts.put(mockFourHourSst.getId(), mockFourHourSst);

        // Add child products to leafProducts map
        mockLeafProducts.put(mockSstFilled.getId(), mockSstFilled);
        mockLeafProducts.put(mockSst.getId(), mockSst);
        mockLeafProducts.put(mockSstAge.getId(), mockSstAge);
        mockLeafProducts.put(mockWindSpeed.getId(), mockWindSpeed);

        // Add child products to allProducts map
        mockAllProducts.put(mockSstFilled.getId(), mockSstFilled);
        mockAllProducts.put(mockSst.getId(), mockSst);
        mockAllProducts.put(mockSstAge.getId(), mockSstAge);
        mockAllProducts.put(mockWindSpeed.getId(), mockWindSpeed);

        // Add SealCTD children
        Product mockSealTrack = Product.builder()
                .title("SealCTD Seal Tracks")
                .id("sealCtd-sealTracks")
                .type("Product")
                .parent(mockSealCtd)
                .build();
        Product mockSealTimeseriesSalinity = Product.builder()
                .title("SealCTD Timeseries Salinity")
                .id("sealCtd-timeseriesSalinity")
                .type("Product")
                .parent(mockSealCtd)
                .build();
        Product mockSealTimeseriesTemp = Product.builder()
                .title("SealCTD Timeseries Temperature")
                .id("sealCtd-timeseriesTemperature")
                .type("Product")
                .parent(mockSealCtd)
                .build();
        Product mockSealTags10 = Product.builder()
                .title("SealCTD Tags 10 days")
                .id("sealCtdTags-10days")
                .type("Product")
                .parent(mockSealCtd)
                .build();
        Product mockSealTagsSal = Product.builder()
                .title("SealCTD Tags Salinity")
                .id("sealCtdTags-salinity")
                .type("Product")
                .parent(mockSealCtd)
                .build();
        Product mockSealTagsTemp = Product.builder()
                .title("SealCTD Tags Temperature")
                .id("sealCtdTags-temperature")
                .type("Product")
                .parent(mockSealCtd)
                .build();
        Product mockSealTagsTimeseries = Product.builder()
                .title("SealCTD Tags timeseries")
                .id("sealCtdTags-timeseries")
                .type("Product")
                .parent(mockSealCtd)
                .build();
        Product mockSealTagsTs = Product.builder()
                .title("SeaCTD Tags TS Plot")
                .id("sealCtdTags-ts")
                .type("Product")
                .parent(mockSealCtd)
                .build();

        mockSealCtd.setChildren(Arrays.asList(
                mockSealTrack,
                mockSealTimeseriesSalinity,
                mockSealTimeseriesTemp,
                mockSealTags10,
                mockSealTagsSal,
                mockSealTagsTemp,
                mockSealTagsTimeseries,
                mockSealTagsTs
        ));

        // Index SealCTD
        mockAllProducts.put(mockSealCtd.getId(), mockSealCtd);
        for (Product p : mockSealCtd.getChildren()) {
            mockLeafProducts.put(p.getId(), p);
            mockAllProducts.put(p.getId(), p);
        }

        // Add product groups to product hierarchy
        mockProductHierarchy.add(mockFourHourSst);
    }

    @Test
    public void testGetAllProducts() throws Exception {
        // Given
        when(productService.getProductHierarchy()).thenReturn(mockProductHierarchy);

        // When & Then
        mockMvc.perform(get("/products")
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(7)))
                .andExpect(jsonPath("$[0].title", is("Snapshot SST")))
                .andExpect(jsonPath("$[0].id", is("snapshotSst")))
                .andExpect(jsonPath("$[5].title", is("SealCTD")))
                .andExpect(jsonPath("$[5].id", is("sealCtd")))
                .andExpect(jsonPath("$[5].type", is("ProductGroup")))
                .andExpect(jsonPath("$[5].children", hasSize(8)))
                .andExpect(jsonPath("$[6].title", is("Four hour SST")))
                .andExpect(jsonPath("$[6].id", is("fourHourSst")))
                .andExpect(jsonPath("$[6].type", is("ProductGroup")))
                .andExpect(jsonPath("$[6].children", hasSize(4)))
                .andExpect(jsonPath("$[6].children[0].title", is("SST Filled")))
                .andExpect(jsonPath("$[6].children[0].id", is("fourHourSst-sstFilled")));
    }

    @Test
    public void testGetAllLeafProducts() throws Exception {
        // Given
        when(productService.getLeafProducts()).thenReturn(mockLeafProducts);

        // When & Then
        mockMvc.perform(get("/products/leaf")
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(17)))
                .andExpect(jsonPath("$[*].id", containsInAnyOrder(
                        "snapshotSst", "surfaceWaves", "argo", "tidalCurrents", "EACMooringArray",
                        "fourHourSst-sstFilled", "fourHourSst-sst", "fourHourSst-sstAge", "fourHourSst-windSpeed",
                        "sealCtd-sealTracks", "sealCtd-timeseriesSalinity", "sealCtd-timeseriesTemperature",
                        "sealCtdTags-10days", "sealCtdTags-salinity", "sealCtdTags-temperature",
                        "sealCtdTags-timeseries", "sealCtdTags-ts")));
    }

    @Test
    public void testGetProductById_ExistingProduct() throws Exception {
        // Given
        String productId = "fourHourSst";
        when(productService.getById(productId)).thenReturn(Optional.of(mockAllProducts.get(productId)));

        // When & Then
        mockMvc.perform(get("/products/{productId}", productId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.title", is("Four hour SST")))
                .andExpect(jsonPath("$.id", is("fourHourSst")))
                .andExpect(jsonPath("$.type", is("ProductGroup")))
                .andExpect(jsonPath("$.children", hasSize(4)));
    }

    @Test
    public void testGetProductById_SealCtdGroup() throws Exception {
        // Given
        String productId = "sealCtd";
        when(productService.getById(productId)).thenReturn(Optional.of(mockAllProducts.get(productId)));

        // When & Then
        mockMvc.perform(get("/products/{productId}", productId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.title", is("SealCTD")))
                .andExpect(jsonPath("$.id", is("sealCtd")))
                .andExpect(jsonPath("$.type", is("ProductGroup")))
                .andExpect(jsonPath("$.children", hasSize(8)));
    }

    @Test
    public void testGetProductById_NonExistingProduct() throws Exception {
        // Given
        String productId = "nonExistingId";
        when(productService.getById(productId)).thenReturn(Optional.empty());

        // When & Then
        mockMvc.perform(get("/products/{productId}", productId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isNotFound());
    }

    @Test
    public void testValidateId_ValidLeafProduct() throws Exception {
        // Given
        String productId = "snapshotSst";
        when(productService.isValidId(productId)).thenReturn(true);
        when(productService.isValidProductId(productId)).thenReturn(true);
        when(productService.isValidProductGroupId(productId)).thenReturn(false);

        // When & Then
        mockMvc.perform(get("/products/validate/{id}", productId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id", is(productId)))
                .andExpect(jsonPath("$.valid", is(true)))
                .andExpect(jsonPath("$.leafProduct", is(true)))
                .andExpect(jsonPath("$.productGroup", is(false)));
    }

    @Test
    public void testValidateId_ValidProductGroup() throws Exception {
        // Given
        String productId = "fourHourSst";
        when(productService.isValidId(productId)).thenReturn(true);
        when(productService.isValidProductId(productId)).thenReturn(false);
        when(productService.isValidProductGroupId(productId)).thenReturn(true);

        // When & Then
        mockMvc.perform(get("/products/validate/{id}", productId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id", is(productId)))
                .andExpect(jsonPath("$.valid", is(true)))
                .andExpect(jsonPath("$.leafProduct", is(false)))
                .andExpect(jsonPath("$.productGroup", is(true)));
    }

    @Test
    public void testValidateId_InvalidId() throws Exception {
        // Given
        String productId = "nonExistingId";
        when(productService.isValidId(productId)).thenReturn(false);
        when(productService.isValidProductId(productId)).thenReturn(false);
        when(productService.isValidProductGroupId(productId)).thenReturn(false);

        // When & Then
        mockMvc.perform(get("/products/validate/{id}", productId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id", is(productId)))
                .andExpect(jsonPath("$.valid", is(false)))
                .andExpect(jsonPath("$.leafProduct", is(false)))
                .andExpect(jsonPath("$.productGroup", is(false)));
    }
}
