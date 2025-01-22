package au.org.aodn.oceancurrent.configuration.remoteJson;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class JsonPathsRoot {
    @NotEmpty(message = "Configuration version must not be empty")
    private String version;

    @Valid
    @NotEmpty(message = "At least one product configuration must be provided")
    private List<JsonPath> products;
}
