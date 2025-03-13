package au.org.aodn.oceancurrent.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Product {
    private String id;
    private String title;
    private String type;

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
}
