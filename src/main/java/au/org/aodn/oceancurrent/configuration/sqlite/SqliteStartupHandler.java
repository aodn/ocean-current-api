package au.org.aodn.oceancurrent.configuration.sqlite;

import au.org.aodn.oceancurrent.service.TagService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

@Slf4j
@Component
public class SqliteStartupHandler implements ApplicationRunner {

    private final SqliteProperties sqliteProperties;
    private final TagService tagService;

    @Autowired
    public SqliteStartupHandler(SqliteProperties sqliteProperties, TagService tagService) {
        this.sqliteProperties = sqliteProperties;
        this.tagService = tagService;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        try {
            ensureSqliteDirectoryExists();
            ensureSqliteFileExists();

            // Check if database has data, auto-download if needed
            ensureDataIsAvailable();

            log.info("SQLite database initialization completed successfully");
        } catch (Exception e) {
            log.warn("SQLite database initialization failed: {}. Will retry download on API calls.", e.getMessage());
        }
    }

    private void ensureSqliteDirectoryExists() throws IOException {
        String localPath = sqliteProperties.getLocalPath();
        log.info("Ensuring SQLite directory exists for path: {}", localPath);

        Path dbPath = Paths.get(localPath).toAbsolutePath();
        Path parentDir = dbPath.getParent();

        if (parentDir != null) {
            if (!Files.exists(parentDir)) {
                try {
                    Files.createDirectories(parentDir);
                    log.info("Created SQLite directory: {}", parentDir.toAbsolutePath());
                } catch (IOException e) {
                    log.error("Failed to create SQLite directory {}: {}", parentDir.toAbsolutePath(), e.getMessage());
                    throw e;
                }
            } else {
                log.info("SQLite directory already exists: {}", parentDir.toAbsolutePath());
            }
        } else {
            log.warn("Parent directory is null for path: {}", dbPath.toAbsolutePath());
        }
    }

    private void ensureSqliteFileExists() throws SQLException {
        String localPath = sqliteProperties.getLocalPath();
        Path dbPath = Paths.get(localPath).toAbsolutePath();

        log.info("Checking if SQLite database file exists: {}", dbPath);

        if (!Files.exists(dbPath)) {
            try {
                // Create empty SQLite database with basic structure
                createEmptyDatabase(dbPath.toString());
                log.info("Created empty SQLite database: {}", dbPath);
            } catch (SQLException e) {
                log.error("Failed to create SQLite database {}: {}", dbPath, e.getMessage());
                throw e;
            }
        } else {
            log.info("SQLite database file already exists: {}", dbPath);
        }
    }

    private void createEmptyDatabase(String dbPath) throws SQLException {
        String url = "jdbc:sqlite:" + dbPath;

        try (Connection conn = DriverManager.getConnection(url);
             Statement stmt = conn.createStatement()) {

            // Create the tags table structure (will be empty until first download)
            String createTableSql = """
                CREATE TABLE IF NOT EXISTS tags (
                    tagfile TEXT NOT NULL,
                    "order" SMALLINT NOT NULL,
                    x SMALLINT NOT NULL,
                    y SMALLINT NOT NULL,
                    sz SMALLINT NOT NULL,
                    title TEXT,
                    url TEXT,
                    PRIMARY KEY (tagfile, "order")
                );
                """;

            String createIndexSql = """
                CREATE INDEX IF NOT EXISTS ix_tags_tagfile ON tags (tagfile);
                """;

            stmt.execute(createTableSql);
            stmt.execute(createIndexSql);

            log.debug("Created empty tags table structure");
        }
    }

    private void ensureDataIsAvailable() {
        try {
            // Check if database has data
            boolean hasData = false;
            try {
                hasData = tagService.isDatabaseAvailable() && tagService.getAllTagFiles().size() > 0;
            } catch (Exception e) {
                log.debug("Error checking database data: {}", e.getMessage());
            }

            if (!hasData) {
                log.info("No data found in database, automatically downloading surface wave data...");
                boolean success = tagService.downloadSqliteDatabase();
                if (success) {
                    log.info("Surface wave data downloaded successfully on startup");
                } else {
                    log.warn("Failed to download surface wave data on startup - will retry on API calls");
                }
            } else {
                log.info("Surface wave data is available in database");
            }
        } catch (Exception e) {
            log.warn("Error ensuring data availability on startup: {}. Will retry on API calls.", e.getMessage());
        }
    }
}
