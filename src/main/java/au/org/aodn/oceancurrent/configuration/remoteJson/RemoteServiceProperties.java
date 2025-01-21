package au.org.aodn.oceancurrent.configuration.remoteJson;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@Configuration
@ConfigurationProperties(prefix = "remote")
public class RemoteServiceProperties {
    @NestedConfigurationProperty
    private JsonConfig json;

    @Data
    public static class JsonConfig {
        @NotBlank(message = "Remote service URL must not be blank")
        @Pattern(regexp = "^(http|https)://.*$", message = "Remote service URL must start with http:// or https://")
        private String baseUrl;
    }
}
