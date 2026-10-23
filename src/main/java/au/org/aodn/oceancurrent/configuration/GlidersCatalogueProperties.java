package au.org.aodn.oceancurrent.configuration;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Data
@Validated
@Configuration
@ConfigurationProperties(prefix = "gliders.catalogue")
public class GlidersCatalogueProperties {
    @NotNull
    @DurationMin(seconds = 1)
    private Duration refreshInterval = Duration.ofHours(1);
}
