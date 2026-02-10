package au.org.aodn.oceancurrent.configuration.elasticsearch;

import au.org.aodn.oceancurrent.util.UrlUtils;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.TransportException;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.Header;
import org.apache.http.HttpHost;
import org.apache.http.message.BasicHeader;
import org.elasticsearch.client.RestClient;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.util.StringUtils;

import java.io.IOException;

/**
 * Configuration for Elasticsearch Cloud connection.
 * Validates credentials and URL at startup, failing the startup if validation fails.
 */
@Configuration
@Slf4j
@Profile("!test")
public class ElasticsearchConfig {
    private final ApplicationContext applicationContext;
    private final ElasticsearchProperties elasticsearchProperties;
    private final String host;
    private final String apiKey;
    private RestClient restClient;

    public ElasticsearchConfig(ApplicationContext applicationContext, ElasticsearchProperties elasticsearchProperties) {
        this.applicationContext = applicationContext;
        this.elasticsearchProperties = elasticsearchProperties;
        this.host = elasticsearchProperties.getHost();
        this.apiKey = elasticsearchProperties.getApiKey();
    }

    @Bean
    public ElasticsearchClient elasticsearchClient() {
        if (!StringUtils.hasText(host) || !StringUtils.hasText(apiKey)) {
            failStartup("Missing required Elasticsearch configuration (host or apiKey)");
        }
        try {
            log.info("Initializing Elasticsearch client");
            log.info("Elasticsearch connection host: {}", UrlUtils.maskSensitiveUrl(host));

            if (log.isDebugEnabled()) {
                log.debug("Elasticsearch index name: {}", elasticsearchProperties.getIndexName());
            }

            restClient = RestClient.builder(HttpHost.create(host))
                    .setDefaultHeaders(new Header[]{new BasicHeader(
                            "Authorization",
                            "ApiKey " + apiKey
                    )})
                    .setRequestConfigCallback(requestConfigBuilder ->
                            requestConfigBuilder
                                    .setConnectTimeout(elasticsearchProperties.getConnectionTimeout())
                                    .setSocketTimeout(elasticsearchProperties.getSocketTimeout())
                    )
                    .build();

            log.info("Elasticsearch client configured with connection timeout: {}ms, socket timeout: {}ms",
                    elasticsearchProperties.getConnectionTimeout(), elasticsearchProperties.getSocketTimeout());

            ElasticsearchTransport transport = new RestClientTransport(restClient, new JacksonJsonpMapper());
            ElasticsearchClient client = new ElasticsearchClient(transport);

            try {
                client.security().authenticate();
                log.info("Successfully authenticated with Elasticsearch");
            } catch (Exception e) {
                handleAuthenticationError(e);
            }

            return client;
        } catch (Exception e) {
            log.error("Failed to initialize Elasticsearch client: {}", e.getMessage());
            failStartup(e.getMessage());
            return null;
        }
    }

    private void handleAuthenticationError(Exception e) {
        if (e instanceof TransportException) {
            String message = e.getMessage();

            if (message.contains("status: 401") || message.contains("Unauthorized")) {
                failStartup("Failed to authenticate with Elasticsearch. Check your API key.");
            } else if (message.contains("status: 404")) {
                failStartup("Elasticsearch endpoint not found. Check your host URL.");
            } else if (isConnectionProblem(message)) {
                failStartup("Cannot connect to Elasticsearch. Check network connectivity.");
            }
        }

        failStartup("Failed to connect to Elasticsearch: " + e.getMessage());
    }

    private boolean isConnectionProblem(String message) {
        if (message == null) return false;

        String[] connectionErrors = {
                "Connection refused",
                "No route to host",
                "Failed to connect",
                "Connection reset",
                "Connection timed out"
        };

        for (String error : connectionErrors) {
            if (message.contains(error)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Forces application to exit during initialization
     */
    private void failStartup(String reason) {
        log.error("Elasticsearch initialization failed: {}", reason);
        System.exit(SpringApplication.exit(applicationContext, () -> 1));
    }

    /**
     * Closes resources when the application shuts down
     */
    @PreDestroy
    public void closeResources() {
        if (restClient != null) {
            try {
                log.info("Closing Elasticsearch client");
                restClient.close();
            } catch (IOException e) {
                log.warn("Error closing Elasticsearch client: {}", e.getMessage());
            }
        }
    }
}
