package au.org.aodn.oceancurrent.controller;

import au.org.aodn.oceancurrent.dto.product.LeafProductResponse;
import au.org.aodn.oceancurrent.dto.product.ProductResponse;
import au.org.aodn.oceancurrent.dto.product.ProductValidationResponse;
import au.org.aodn.oceancurrent.model.Product;
import au.org.aodn.oceancurrent.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    /**
     * Get the complete product hierarchy including product groups and leaf products
     */
    @GetMapping
    public ResponseEntity<List<ProductResponse>> getAllProducts() {
        List<Product> hierarchy = productService.getProductHierarchy();

        List<ProductResponse> response = hierarchy.stream()
                .map(ProductResponse::fromProduct)
                .collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }

    /**
     * Get all leaf products (both standalone and those within product groups)
     */
    @GetMapping("/leaf")
    public ResponseEntity<List<LeafProductResponse>> getAllLeafProducts() {
        Map<String, Product> leafProducts = productService.getLeafProducts();

        List<LeafProductResponse> response = leafProducts.values().stream()
                .map(LeafProductResponse::fromProduct)
                .collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }

    /**
     * Get details of a specific product by ID
     */
    @GetMapping("/{productId}")
    public ResponseEntity<ProductResponse> getProductById(@PathVariable String productId) {
        return productService.getById(productId)
                .map(ProductResponse::fromProduct)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Validate if a product ID exists (checks both leaf products and product groups)
     */
    @GetMapping("/validate/{id}")
    public ResponseEntity<ProductValidationResponse> validateId(@PathVariable String id) {
        boolean isValid = productService.isValidId(id);
        boolean isLeafProduct = productService.isValidProductId(id);
        boolean isProductGroup = productService.isValidProductGroupId(id);

        ProductValidationResponse response = ProductValidationResponse.builder()
                .id(id)
                .isValid(isValid)
                .isLeafProduct(isLeafProduct)
                .isProductGroup(isProductGroup)
                .build();

        return ResponseEntity.ok(response);
    }
}