package au.org.aodn.oceancurrent.dto.product;

import au.org.aodn.oceancurrent.model.Product;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ProductResponse {
    private String title;
    private String id;
    private Boolean regionRequired;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String type;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private List<ProductResponse> children;

    /**
     * Create a ProductResponse from a Product model
     */
    public static ProductResponse fromProduct(Product product) {
        ProductResponse response = new ProductResponse();
        response.setTitle(product.getTitle());
        response.setId(product.getId());
        response.setRegionRequired(product.isRegionRequired());
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
