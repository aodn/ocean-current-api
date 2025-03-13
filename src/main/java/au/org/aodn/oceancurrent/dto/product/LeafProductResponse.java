package au.org.aodn.oceancurrent.dto.product;

import au.org.aodn.oceancurrent.model.Product;
import lombok.Data;

@Data
public class LeafProductResponse {
    private String title;
    private String id;
    private boolean standalone;
    private String parentId;
    private String parentTitle;

    /**
     * Create a LeafProductResponse from a Product model
     */
    public static LeafProductResponse fromProduct(Product product) {
        LeafProductResponse response = new LeafProductResponse();
        response.setTitle(product.getTitle());
        response.setId(product.getId());
        response.setStandalone(product.isStandaloneLeaf());

        // Include parent information if available
        if (product.getParent() != null) {
            response.setParentId(product.getParent().getId());
            response.setParentTitle(product.getParent().getTitle());
        }

        return response;
    }
}