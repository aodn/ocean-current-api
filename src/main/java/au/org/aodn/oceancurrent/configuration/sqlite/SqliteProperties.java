package au.org.aodn.oceancurrent.configuration.sqlite;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "sqlite")
public class SqliteProperties {

    private String remotePath;
    private String localPath;
    private Download download = new Download();

    @Data
    public static class Download {
        private Cron cron = new Cron();
        private int connectTimeout = 30000;
        private int readTimeout = 60000;

        @Data
        public static class Cron {
            private String expression = "0 0 * * * ?"; // every hour
        }
    }
}
