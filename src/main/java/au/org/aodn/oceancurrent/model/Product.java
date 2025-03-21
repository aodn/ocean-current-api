package au.org.aodn.oceancurrent.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Product {
    private String id;
    private String title;
    private String type;
    private Boolean regionRequired;
    private Boolean depthRequired;

    @ToString.Exclude
    private Product parent;

    private List<Product> children = new ArrayList<>();

    public boolean isLeaf() {
        return !"ProductGroup".equals(this.type);
    }

    public boolean isProductGroup() {
        return "ProductGroup".equals(this.type);
    }

    public boolean isStandaloneLeaf() {
        return isLeaf() && parent == null;
    }

    public boolean isChildLeaf() {
        return isLeaf() && parent != null;
    }

    public String getParentId() {
        return parent != null ? parent.getId() : null;
    }

    public String getParentTitle() {
        return parent != null ? parent.getTitle() : null;
    }

    public boolean isRegionRequired() {
        return Optional.ofNullable(regionRequired).orElse(true); // Default to true
    }

    public boolean isDepthRequired() {
        return Optional.ofNullable(depthRequired).orElse(false); // Default to false
    }
}
