package au.org.aodn.oceancurrent.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Generic tag response DTO that can be used for any product type.
 * This provides a common structure while allowing product-specific data in the tags field.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class GenericTagResponse {

    private String productType;
    private String tagFile;
    private List<Map<String, Object>> tags;
    private int count;

    public GenericTagResponse(String productType, String tagFile, List<Map<String, Object>> tags) {
        this.productType = productType;
        this.tagFile = tagFile;
        this.tags = tags;
        this.count = tags != null ? tags.size() : 0;
    }
}
