package au.org.aodn.oceancurrent.configuration;

import au.org.aodn.oceancurrent.configuration.product.ProductProperties;
import au.org.aodn.oceancurrent.model.Product;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@org.springframework.test.context.TestPropertySource(properties = {
        "spring.config.import=classpath:/config/products.yaml"
})
class ProductConfigIntegrationTest {

    @Autowired
    private ProductProperties productProperties;

    @Autowired
    private Map<String, Product> allProductsMap;

    @Autowired
    private Map<String, Product> productMap; // leaf products

    @Autowired
    private Map<String, Product> productGroupMap; // product groups

    @Test
    void shouldLoadProductsYamlAndContainSealCtdTracksId() {
        List<Product> hierarchy = productProperties.getProducts();
        assertThat(hierarchy).isNotEmpty();

        // Validate via allProductsMap to include children
        assertThat(allProductsMap).containsKey("sealCtd");
        assertThat(allProductsMap).containsKey("sealCtd-sealTracks");

        // Cross-check that the SealCTD group has a child with expected id
        Product sealCtd = allProductsMap.get("sealCtd");
        List<String> childIds = sealCtd.getChildren().stream().map(Product::getId).collect(Collectors.toList());
        assertThat(childIds).contains("sealCtd-sealTracks");
    }

    @Test
    void idsAndTitlesShouldBeNonEmptyAndUnique() {
        // All products included in allProductsMap
        assertThat(allProductsMap).isNotEmpty();
        List<String> ids = allProductsMap.values().stream().map(Product::getId).collect(Collectors.toList());
        List<String> titles = allProductsMap.values().stream().map(Product::getTitle).collect(Collectors.toList());

        assertThat(ids).allMatch(id -> id != null && !id.isBlank());
        assertThat(titles).allMatch(title -> title != null && !title.isBlank());
        assertThat(ids).hasSameSizeAs(ids.stream().distinct().collect(Collectors.toList()));
    }

    @Test
    void productGroupsShouldHaveChildrenAndChildrenHaveParentLinks() {
        assertThat(productGroupMap).isNotEmpty();
        productGroupMap.values().forEach(group -> {
            assertThat(group.getChildren()).isNotNull();
            assertThat(group.getChildren()).isNotEmpty();
            group.getChildren().forEach(child -> {
                // Child should be present in leaf product map
                assertThat(productMap).containsKey(child.getId());
                // Parent linkage should be consistent
                assertThat(child.getParentId()).isEqualTo(group.getId());
                assertThat(child.getParentTitle()).isEqualTo(group.getTitle());
            });
        });
    }

    @Test
    void mapsShouldBeConsistentInCounts() {
        // allProductsMap should be the union of productMap (leaves) and productGroupMap (groups)
        assertThat(allProductsMap.size()).isEqualTo(productMap.size() + productGroupMap.size());
    }

    @Test
    void specificFlagsForCurrentMetersShouldMatchConfig() {
        // Depth required for plot
        assertThat(productMap.get("currentMetersPlot-49")).isNotNull();
        assertThat(productMap.get("currentMetersPlot-49").isDepthRequired()).isTrue();

        // Region not required for calendar and region
        assertThat(productMap.get("currentMetersCalendar-49")).isNotNull();
        assertThat(productMap.get("currentMetersCalendar-49").isRegionRequired()).isFalse();
        assertThat(productMap.get("currentMetersRegion-49")).isNotNull();
        assertThat(productMap.get("currentMetersRegion-49").isRegionRequired()).isFalse();
    }

    @Test
    void defaultsShouldApplyWhenFlagsUnset() {
        // Example leaf products without explicit flags in YAML
        Product sealTracks = productMap.get("sealCtd-sealTracks");
        assertThat(sealTracks).isNotNull();
        assertThat(sealTracks.isRegionRequired()).isTrue(); // default true
        assertThat(sealTracks.isDepthRequired()).isFalse(); // default false

        Product chlA = productMap.get("oceanColour-chlA");
        if (chlA != null) {
            assertThat(chlA.isRegionRequired()).isTrue();
            assertThat(chlA.isDepthRequired()).isFalse();
        }
    }

    @Test
    void sealCtdGroupShouldContainAllExpectedChildren() {
        Product sealCtd = productGroupMap.get("sealCtd");
        assertThat(sealCtd).isNotNull();
        List<String> childIds = sealCtd.getChildren().stream().map(Product::getId).collect(Collectors.toList());
        assertThat(childIds).contains(
                "sealCtd-sealTracks",
                "sealCtd-timeseriesSalinity",
                "sealCtd-timeseriesTemperature",
                "sealCtdTags-10days",
                "sealCtdTags-salinity",
                "sealCtdTags-temperature",
                "sealCtdTags-timeseries",
                "sealCtdTags-ts"
        );
    }
}
