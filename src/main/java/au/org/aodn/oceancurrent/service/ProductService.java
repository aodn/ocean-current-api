package au.org.aodn.oceancurrent.service;

import au.org.aodn.oceancurrent.configuration.product.ProductProperties;
import au.org.aodn.oceancurrent.model.Product;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class ProductService {

    private final Map<String, Product> productMap; // Only leaf products
    private final Map<String, Product> productGroupMap; // Only product groups
    private final Map<String, Product> allProductsMap; // All products and product groups

    private final ProductProperties productProperties;

    public boolean isValidProductId(String productId) {
        return productMap.containsKey(productId);
    }

    public boolean isValidProductGroupId(String productGroupId) {
        return productGroupMap.containsKey(productGroupId);
    }

    public boolean isValidId(String id) {
        return allProductsMap.containsKey(id);
    }

    public Optional<Product> getProductById(String productId) {
        return Optional.ofNullable(productMap.get(productId));
    }

    public Optional<Product> getProductGroupById(String productGroupId) {
        return Optional.ofNullable(productGroupMap.get(productGroupId));
    }

    public Optional<Product> getById(String id) {
        return Optional.ofNullable(allProductsMap.get(id));
    }

    public Map<String, Product> getLeafProducts() {
        return productMap;
    }

    public Map<String, Product> getProductGroups() {
        return productGroupMap;
    }

    public List<Product> getProductHierarchy() {
        return productProperties.getProducts();
    }
}