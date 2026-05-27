package au.org.aodn.oceancurrent.configuration;

import au.org.aodn.oceancurrent.configuration.remoteJson.JsonPath;
import au.org.aodn.oceancurrent.configuration.remoteJson.JsonPathsRoot;
import au.org.aodn.oceancurrent.constant.ConfigConstants;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class JsonPathsConfigTest {

    private static JsonPathsRoot config;

    @BeforeAll
    static void loadConfig() throws IOException {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        config = mapper.readValue(
                new ClassPathResource(ConfigConstants.CONFIG_FILE_PATH).getInputStream(),
                JsonPathsRoot.class
        );
    }

    @Test
    void swotGslaShouldHaveSshJsonPath() {
        Optional<JsonPath> entry = config.getProducts().stream()
                .filter(p -> p.getProduct().equals("SWOT GSLA"))
                .findFirst();
        assertThat(entry).isPresent();
        assertThat(entry.get().getPaths()).containsExactly("/DR_SWOT/SSH/SSH.json");
    }

    @Test
    void swotGslaShouldNotIndexMdt() {
        Optional<JsonPath> entry = config.getProducts().stream()
                .filter(p -> p.getProduct().equals("SWOT GSLA"))
                .findFirst();
        assertThat(entry).isPresent();
        assertThat(entry.get().getPaths()).noneMatch(p -> p.contains("MDT") || p.contains("MDTCMEMS"));
    }
}
