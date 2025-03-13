package au.org.aodn.oceancurrent.dto.product;

import au.org.aodn.oceancurrent.model.Product;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ProductResponse {
    private String title;
    private String id;
    private String type;
    private List<ProductResponse> children = new ArrayList<>();

    /**
     * Create a ProductResponse from a Product model
     */
    public static ProductResponse fromProduct(Product product) {
        ProductResponse response = new ProductResponse();
        response.setTitle(product.getTitle());
        response.setId(product.getId());
        response.setType(product.getType());

        if (product.getChildren() != null && !product.getChildren().isEmpty()) {
            List<ProductResponse> childResponses = new ArrayList<>();
            for (Product child : product.getChildren()) {
                childResponses.add(fromProduct(child));
            }
            response.setChildren(childResponses);
        }

        return response;
    }
}