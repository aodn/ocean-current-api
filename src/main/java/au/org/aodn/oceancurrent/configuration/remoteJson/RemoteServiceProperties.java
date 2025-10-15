package au.org.aodn.oceancurrent.configuration.remoteJson;

import jakarta.annotation.PostConstruct;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

@Slf4j
@Data
@Validated
@Configuration
@ConfigurationProperties(prefix = "remote")
public class RemoteServiceProperties {
    @NotBlank(message = "Remote service URL must not be blank")
    @Pattern(regexp = "^(https)://.*$", message = "Remote service URL must start with https://")
    private String baseUrl;

    /**
     * CloudFront proxy path for accessing remote resources
     */
    private String resourcePath = "/resource/";

    @PostConstruct
    public void logConfig() {
        log.info("Remote service base URL: {}", baseUrl);
        log.info("Remote resource path (CloudFront proxy): {}", resourcePath);
    }

    /**
     * Get the full URL for accessing remote resource files through CloudFront proxy
     * @return base URL + CloudFront proxy path (e.g., "https://example.com/resource/")
     */
    public String getResourceBaseUrl() {
        return baseUrl + resourcePath;
    }
}
