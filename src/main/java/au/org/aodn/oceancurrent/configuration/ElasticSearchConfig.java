package au.org.aodn.oceancurrent.configuration;

import au.org.aodn.oceancurrent.constant.ElasticsearchErrorType;
import au.org.aodn.oceancurrent.exception.ElasticsearchConnectionException;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.TransportException;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import lombok.AccessLevel;
import lombok.Data;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.Header;
import org.apache.http.HttpHost;
import org.apache.http.message.BasicHeader;
import org.elasticsearch.client.RestClient;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Configuration
//@ConfigurationProperties(prefix = "elasticsearch")
//@EnableConfigurationProperties(ElasticsearchProperties.class)
@Slf4j
@RequiredArgsConstructor
public class ElasticSearchConfig implements InitializingBean {
//    private String host;
//    private String apiKey;

    private final ElasticSearchProperties properties;
    private final ApplicationContext applicationContext;
    private final RestClient restClient;

    @Setter(AccessLevel.NONE)
    private final String[] CONNECTION_ERROR_PATTERNS = {
            "Connection refused",
            "No route to host",
            "Failed to connect",
            "Connection reset",
            "Connection timed out"
    };
    
    @Override
    public void afterPropertiesSet() throws Exception {

    }

    @Bean
    public ElasticsearchClient elasticsearchClient() {
        // Validate required configuration
        if (!StringUtils.hasText(properties.getHost()) || !StringUtils.hasText(properties.getApiKey())) {
            failStartup("Missing required Elasticsearch configuration (host or apiKey)");
        }
        try {
            log.info("Initializing Elasticsearch client");

            RestClient restClient = RestClient.builder(
                            HttpHost.create(properties.getHost())
                    )
                    .setDefaultHeaders(new Header[]{new BasicHeader("Authorization", "ApiKey " + apiKey)})
                    .build();

            ElasticsearchTransport transport = new RestClientTransport(restClient, new JacksonJsonpMapper());
            ElasticsearchClient client = new ElasticsearchClient(transport);

            verifyConnection(client);

            return client;
        } catch (Exception e) {
            if (e instanceof ElasticsearchConnectionException) {
                throw e;
            }

            log.error("Failed to initialize Elasticsearch client with host: {}", host, e);

            throw new ElasticsearchConnectionException(
                    host,
                    ElasticsearchErrorType.CONNECTION_ERROR,
                    "Failed to initialize Elasticsearch client. Please check the configuration.",
                    e
            );
        }
    }

    /**
     * Verifies connection to Elasticsearch by performing a ping request
     * and throws appropriate exceptions based on error patterns
     */
    private void verifyConnection(ElasticsearchClient client) {
        try {
//            client.ping();
            client.cluster().health();
            log.info("Successfully connected to Elasticsearch at {}", host);
        } catch (Exception e) {
            if (e instanceof TransportException) {
                handleTransportException((TransportException) e);
            }

            // General error
            log.error("Error connecting to Elasticsearch at {}", host);
            throw new ElasticsearchConnectionException(
                    host,
                    ElasticsearchErrorType.GENERAL_ERROR,
                    "Failed to connect to Elasticsearch due to an unexpected error.",
                    e
            );
        }
    }

    /**
     * Analyzes TransportException to determine the specific error type
     */
    private void handleTransportException(TransportException e) {
        String errorMessage = e.getMessage();

        if (errorMessage.contains("status: 401") || errorMessage.contains("Unauthorized")) {
            // Authentication error
            log.error("Authentication failed for Elasticsearch at {}", host);
            throw new ElasticsearchConnectionException(
                    host,
                    ElasticsearchErrorType.AUTHENTICATION_ERROR,
                    "Failed to authenticate with Elasticsearch. The API key may be incorrect or expired.",
                    e
            );
        } else if (errorMessage.contains("status: 404")) {
            // Endpoint not found
            log.error("Elasticsearch endpoint not found at {}", host);
            throw new ElasticsearchConnectionException(
                    host,
                    ElasticsearchErrorType.ENDPOINT_ERROR,
                    "Elasticsearch endpoint not found. The URL path may be incorrect.",
                    e
            );
        } else if (containsAnyPattern(errorMessage, CONNECTION_ERROR_PATTERNS)) {
            // Host not reachable
            log.error("Failed to connect to Elasticsearch host at {}", host);
            throw new ElasticsearchConnectionException(
                    host,
                    ElasticsearchErrorType.CONNECTION_ERROR,
                    "Failed to connect to Elasticsearch host. The URL may be incorrect or the service is not available.",
                    e
            );
        }
    }

    /**
     * Helper method to check if a string contains any of the given patterns
     */
    private boolean containsAnyPattern(String text, String[] patterns) {
        for (String pattern : patterns) {
            if (text.contains(pattern)) {
                return true;
            }
        }
        return false;
    }

}
