package au.org.aodn.oceancurrent.configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.util.List;

@Configuration
@Profile("!prod")
public class OpenApiConfig {

    @Value("${springdoc.swagger-ui.server.domain:http://localhost:8080}")
    private String domain;

    @Value("${server.servlet.context-path}")
    private String contextPath;

    @Bean
    public OpenAPI customOpenAPI() {
        String serverUrl = domain + contextPath;

        Server server = new Server()
                .url(serverUrl)
                .description("Generated Server URL");

        return new OpenAPI()
                .servers(List.of(server))
                .info(new Info()
                        .title("Ocean Current API")
                        .version("1.0")
                        .description("API Documentation for Ocean Current"));
    }
}
