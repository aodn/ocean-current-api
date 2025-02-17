package au.org.aodn.oceancurrent.configuration.remoteJson;

import jakarta.annotation.PostConstruct;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

@Slf4j
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
        @Pattern(regexp = "^(https)://.*$", message = "Remote service URL must start with https://")
        private String baseUrl;
    }

    @PostConstruct
    public void logConfig() {
        log.info("Remote JSON file service base URL: {}", json.getBaseUrl());
    }
}
