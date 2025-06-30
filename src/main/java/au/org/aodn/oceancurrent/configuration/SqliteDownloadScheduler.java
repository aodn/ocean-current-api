package au.org.aodn.oceancurrent.configuration;

import au.org.aodn.oceancurrent.configuration.sqlite.SqliteProperties;
import au.org.aodn.oceancurrent.service.tags.TagService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(name = "sqlite.download.cron.expression")
public class SqliteDownloadScheduler {

    private final TagService tagService;
    private final SqliteProperties sqliteProperties;

    @Autowired
    public SqliteDownloadScheduler(TagService tagService, SqliteProperties sqliteProperties) {
        this.tagService = tagService;
        this.sqliteProperties = sqliteProperties;
    }

    @Scheduled(cron = "${sqlite.download.cron.expression}")
    public void downloadSqliteDatabase() {
        log.info("Starting scheduled SQLite database download");

        try {
            boolean success = tagService.downloadData("surface-waves");
            if (success) {
                log.info("Scheduled SQLite database download completed successfully");
            } else {
                log.warn("Scheduled SQLite database download failed");
            }
        } catch (Exception e) {
            log.error("Error during scheduled SQLite database download: {}", e.getMessage(), e);
        }
    }
}
