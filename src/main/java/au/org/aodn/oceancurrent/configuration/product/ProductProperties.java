package au.org.aodn.oceancurrent.configuration.product;

import au.org.aodn.oceancurrent.model.Product;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "ocean-current")
@Data
public class ProductProperties {
    private List<Product> products = new ArrayList<>();
}