package au.org.aodn.oceancurrent.configuration.elasticsearch;

import lombok.Data;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;

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
    @Max(100)
    private int reindexValidationThresholdPercent = 80;

    /**
     * Socket timeout in milliseconds for Elasticsearch operations.
     * RestClient default is 30 seconds. Increased to accommodate bulk indexing operations.
     */
    private int socketTimeout = 60000;

    /**
     * Connection timeout in milliseconds for establishing a connection to Elasticsearch.
     * RestClient default is 1 second.
     */
    private int connectionTimeout = 5000;

    /**
     * When true, skips the productId check during reindex validation.
     * Use only when intentionally updating productIds in source files.
     * Controlled via ES_SKIP_PRODUCT_ID_CHECK env var backed by SSM, updated in cd_production.yaml workflow.
     * Reset to false and redeploy after the scheduled run completes.
     */
    private boolean reindexValidationSkipProductIdCheck = false;
}
