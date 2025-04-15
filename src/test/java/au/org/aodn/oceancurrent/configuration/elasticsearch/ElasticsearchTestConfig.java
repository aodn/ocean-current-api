package au.org.aodn.oceancurrent.configuration.elasticsearch;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.utility.DockerImageName;

@Configuration
@Profile("test")
public class ElasticsearchTestConfig {

    private static final Logger log = LoggerFactory.getLogger(ElasticsearchTestConfig.class);

    private static final String ELASTICSEARCH_IMAGE = "docker.elastic.co/elasticsearch/elasticsearch:";

    /**
     * Creates and starts an Elasticsearch container for testing.
     * Container is configured with minimal settings for testing purposes.
     */
    @Bean(initMethod = "start", destroyMethod = "stop")
    public ElasticsearchContainer elasticsearchContainer(
            @Value("${elasticsearch.docker.elasticVersion:8.17.4}") String version
    ) {
        ElasticsearchContainer container = new ElasticsearchContainer(
                DockerImageName.parse(ELASTICSEARCH_IMAGE + version)
        )
                .withEnv("xpack.security.enabled", "false")
                .withEnv("discovery.type", "single-node")
                .withEnv("ES_JAVA_OPTS", "-Xms512m -Xmx512m");

        log.info("Creating Elasticsearch test container with image: {}", ELASTICSEARCH_IMAGE);
        return container;
    }

    /**
     * Creates an Elasticsearch client connected to the test container.
     * This bean overrides the production client for testing.
     */
    @Bean
    @Primary
    @DependsOn("elasticsearchContainer")
    public ElasticsearchClient testElasticsearchClient(ElasticsearchContainer elasticsearchContainer) {
        String httpHostAddress = elasticsearchContainer.getHttpHostAddress();
        log.info("Creating Elasticsearch client connected to test container at: {}", httpHostAddress);

        RestClient restClient = RestClient.builder(
                HttpHost.create(httpHostAddress)
        ).build();

        ElasticsearchTransport transport = new RestClientTransport(
                restClient,
                new JacksonJsonpMapper()
        );

        return new ElasticsearchClient(transport);
    }

}