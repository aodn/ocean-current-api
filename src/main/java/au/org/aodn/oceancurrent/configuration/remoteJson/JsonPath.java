package au.org.aodn.oceancurrent.configuration.remoteJson;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import lombok.ToString;

import java.util.List;

@Data
@ToString
public class JsonPath {
    @NotEmpty(message = "Product name must not be empty")
    private String product;

    @NotEmpty(message = "Product paths must not be empty")
    private List<@Pattern(regexp = "^/.*", message = "Path must start with /") String> paths;

    @NotEmpty(message = "Product description must not be empty")
    private String description;
}
