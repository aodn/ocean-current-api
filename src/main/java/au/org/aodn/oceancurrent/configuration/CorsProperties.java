package au.org.aodn.oceancurrent.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Data
@ConfigurationProperties(prefix = "app.cors")
@Configuration
public class CorsProperties {
    private List<String> allowedOrigins;
    private List<String> allowedMethods;
    private boolean allowCredentials;
}
