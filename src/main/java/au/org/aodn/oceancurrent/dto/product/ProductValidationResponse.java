package au.org.aodn.oceancurrent.dto.product;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ProductValidationResponse {
    private String id;
    private boolean isValid;
    private boolean isLeafProduct;
    private boolean isProductGroup;
}
