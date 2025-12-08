package au.org.aodn.oceancurrent.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@ConfigurationProperties(prefix = "monitoring")
@Configuration
public class MonitoringProperties {
    private String apiKey;
}
