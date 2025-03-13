package au.org.aodn.oceancurrent.configuration.product;

import au.org.aodn.oceancurrent.model.Product;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Configuration
@RequiredArgsConstructor
public class ProductConfig {

    private final ProductProperties productProperties;

    @Bean
    public Map<String, Product> productMap() {
        List<Product> products = productProperties.getProducts();
        Map<String, Product> productMap = new HashMap<>();

        // Process each product
        for (Product product : products) {
            // If it's a leaf product (standalone), add it to the map
            if (product.isLeaf()) {
                productMap.put(product.getId(), product);
            }

            // If it has children (product group), set up parent relationship and add all child products
            if (product.getChildren() != null && !product.getChildren().isEmpty()) {
                for (Product child : product.getChildren()) {
                    // Set parent reference
                    child.setParent(product);

                    // Add to product map
                    productMap.put(child.getId(), child);
                }
            }
        }

        return productMap;
    }

    @Bean
    public Map<String, Product> productGroupMap() {
        List<Product> products = productProperties.getProducts();
        Map<String, Product> productGroupMap = new HashMap<>();

        // Add only product group products
        for (Product product : products) {
            if (product.isProductGroup()) {
                productGroupMap.put(product.getId(), product);
            }
        }

        return productGroupMap;
    }

    @Bean
    public Map<String, Product> allProductsMap() {
        List<Product> products = productProperties.getProducts();
        Map<String, Product> allMap = new HashMap<>();

        // Add all products (both leaf and product groups)
        for (Product product : products) {
            allMap.put(product.getId(), product);

            if (product.getChildren() != null) {
                for (Product child : product.getChildren()) {
                    allMap.put(child.getId(), child);
                }
            }
        }

        return allMap;
    }
}