package au.org.aodn.oceancurrent.configuration.elasticsearch;

import lombok.Data;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import jakarta.validation.constraints.Min;

@Data
@Configuration
@ConfigurationProperties(prefix = "elasticsearch")
public class ElasticsearchProperties {
    private String host;
    private String apiKey;
    private int maxResultWindow = 50000;
    private String indexName = "ocean-current-files";

    /**
     * Minimum percentage threshold for new index document count compared to old index.
     * Value should be between 0 and 100. Default is 80 (meaning new index must have at least 80% of old index documents).
     */
    @Min(0)
    @Min(100)
    private int reindexValidationThresholdPercent = 80;
}
