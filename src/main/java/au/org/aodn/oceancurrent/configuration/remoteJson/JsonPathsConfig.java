package au.org.aodn.oceancurrent.configuration.remoteJson;

import au.org.aodn.oceancurrent.exception.RemoteFilePathJsonConfigurationException;
import au.org.aodn.oceancurrent.constant.ConfigConstants;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.validation.annotation.Validated;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

@Configuration
@Slf4j
@Validated
public class JsonPathsConfig {
    private final Validator validator;
    private final ObjectMapper yamlObjectMapper;

    @Bean
    public JsonPathsRoot jsonPathsRoot() {
        try {
            String configPath = ConfigConstants.CONFIG_FILE_PATH;
            log.info("Loading JSON paths configuration from: {}", configPath);

            JsonPathsRoot config = loadConfiguration(configPath);
            validateConfiguration(config);

            log.info("Successfully loaded JSON paths configuration. Version: {}", config.getVersion());
            return config;
        } catch (IOException e) {
            throw new RemoteFilePathJsonConfigurationException("Failed to load JSON paths configuration", e);
        }
    }

    private JsonPathsRoot loadConfiguration(String configPath) throws IOException {
        try (InputStream is = new ClassPathResource(configPath).getInputStream()) {
            return yamlObjectMapper.readValue(is, JsonPathsRoot.class);
        }
    }

    private void validateConfiguration(JsonPathsRoot config) {
        Set<ConstraintViolation<JsonPathsRoot>> violations = validator.validate(config);
        if (!violations.isEmpty()) {
            StringBuilder errorMessage = new StringBuilder("Configuration validation failed:");
            violations.forEach(violation ->
                    errorMessage.append("\n- ")
                            .append(violation.getPropertyPath())
                            .append(": ")
                            .append(violation.getMessage())
            );
            throw new RemoteFilePathJsonConfigurationException(errorMessage.toString());
        }
    }

    public JsonPathsConfig(
            Validator validator,
            @Qualifier("yamlObjectMapper") ObjectMapper yamlObjectMapper) {
        this.validator = validator;
        this.yamlObjectMapper = yamlObjectMapper;
    }
}
