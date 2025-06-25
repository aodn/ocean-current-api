package au.org.aodn.oceancurrent.service;

import au.org.aodn.oceancurrent.configuration.sqlite.SqliteProperties;
import au.org.aodn.oceancurrent.dto.WaveTagResponse;
import au.org.aodn.oceancurrent.model.WaveTag;
import au.org.aodn.oceancurrent.repository.WaveTagRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class TagService {

            private final SqliteProperties sqliteProperties;
    private final WaveTagRepository waveTagRepository;

    @Autowired
    public TagService(SqliteProperties sqliteProperties, WaveTagRepository waveTagRepository) {
        this.sqliteProperties = sqliteProperties;
        this.waveTagRepository = waveTagRepository;
    }

    /**
     * Download SQLite database from remote server
     */
    public boolean downloadSqliteDatabase() {
        try {
            log.info("Starting download of SQLite database from: {}", sqliteProperties.getRemoteUrl());

            URL url = new URL(sqliteProperties.getRemoteUrl());
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(sqliteProperties.getDownload().getConnectTimeout());
            connection.setReadTimeout(sqliteProperties.getDownload().getReadTimeout());
            connection.setRequestMethod("GET");

            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                log.error("Failed to download SQLite database. Response code: {}", responseCode);
                return false;
            }

                        // Create directory if it doesn't exist
            Path localPath = Paths.get(sqliteProperties.getLocalPath());
            if (localPath.getParent() != null) {
                Files.createDirectories(localPath.getParent());
            }

            // Download and save the file, replacing any existing file
            try (InputStream inputStream = connection.getInputStream()) {
                Files.copy(inputStream, localPath, StandardCopyOption.REPLACE_EXISTING);
                long fileSize = Files.size(localPath);
                log.info("SQLite database file downloaded successfully: {} bytes", fileSize);

                // Verify the downloaded file has data by checking its size
                if (fileSize < 1024) { // Less than 1KB suggests empty or corrupt file
                    log.warn("Downloaded SQLite file seems too small ({}bytes). May be empty or corrupt.", fileSize);
                }
            }

            // Give the file system a moment to flush and verify the download
            Thread.sleep(1000); // 1 second delay
            verifyDownloadedData();

            log.info("Successfully downloaded SQLite database to: {} at {}",
                sqliteProperties.getLocalPath(), LocalDateTime.now());
            return true;

        } catch (Exception e) {
            log.error("Error downloading SQLite database: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Get all tags for a specific tagfile
     */
    public WaveTagResponse getTagsByTagfile(String tagfile) {
        try {
            List<WaveTag> tags = waveTagRepository.findByTagfileOrderByOrder(tagfile);

                        List<WaveTagResponse.TagData> tagDataList = tags.stream()
                .map(tag -> new WaveTagResponse.TagData(
                    tag.getX(),
                    tag.getY(),
                    tag.getSz(),
                    tag.getTitle(),
                    tag.getUrl()
                ))
                .collect(Collectors.toList());

            return new WaveTagResponse(tagfile, tagDataList);

        } catch (Exception e) {
            log.error("Error querying tags for tagfile {}: {}", tagfile, e.getMessage(), e);
            throw new RuntimeException("Failed to query tags for tagfile: " + tagfile, e);
        }
    }

    /**
     * Get all available tagfiles
     */
    public List<String> getAllTagfiles() {
        try {
            return waveTagRepository.findDistinctTagfiles();
        } catch (Exception e) {
            log.error("Error getting all tagfiles: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to get tagfiles", e);
        }
    }

    /**
     * Check if SQLite database file exists locally and is accessible
     */
    public boolean isDatabaseAvailable() {
        try {
            // Ensure directory and basic file structure exist first
            ensureDatabasePathExists();

            Path dbPath = Paths.get(sqliteProperties.getLocalPath()).toAbsolutePath();
            if (!Files.exists(dbPath) || !Files.isRegularFile(dbPath)) {
                log.debug("SQLite database file does not exist: {}", dbPath);
                return false;
            }
            // Test database connectivity by trying to query records
            try {
                List<String> tagfiles = waveTagRepository.findDistinctTagfiles();
                log.debug("Database is available with {} tagfiles (JPA check)", tagfiles.size());

                // Double-check with direct SQL if JPA reports no data
                if (tagfiles.isEmpty()) {
                    log.debug("JPA reports no tagfiles, checking with direct SQL...");
                    String url = "jdbc:sqlite:" + sqliteProperties.getLocalPath();
                    try (Connection conn = DriverManager.getConnection(url);
                         Statement stmt = conn.createStatement()) {
                        var rs = stmt.executeQuery("SELECT COUNT(DISTINCT tagfile) as count FROM tags");
                        if (rs.next()) {
                            int directCount = rs.getInt("count");
                            log.debug("Direct SQL reports {} tagfiles - JPA connection may be cached", directCount);
                        }
                    } catch (SQLException sqlEx) {
                        log.debug("Direct SQL check also failed: {}", sqlEx.getMessage());
                    }
                }

            } catch (Exception dbException) {
                log.debug("Database query test failed: {}", dbException.getMessage());
                return false;
            }
            return true;
        } catch (Exception e) {
            log.debug("Database connectivity test failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Check if tagfile exists in database
     */
    public boolean tagfileExists(String tagfile) {
        try {
            return waveTagRepository.existsByTagfile(tagfile);
        } catch (Exception e) {
            log.error("Error checking if tagfile exists: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Check if database has any data
     */
    public boolean hasData() {
        try {
            return !getAllTagfiles().isEmpty();
        } catch (Exception e) {
            log.debug("Error checking if database has data: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Ensure database directory and empty file exist as fallback
     * This is a safety net in case the SqliteStartupHandler hasn't run yet
     */
    private void ensureDatabasePathExists() {
        try {
            String localPath = sqliteProperties.getLocalPath();
            Path dbPath = Paths.get(localPath).toAbsolutePath();
            Path parentDir = dbPath.getParent();

            // Create directory structure if needed
            if (parentDir != null && !Files.exists(parentDir)) {
                Files.createDirectories(parentDir);
                log.info("Created SQLite directory as fallback: {}", parentDir);
            }

            // Create empty database file if needed
            if (!Files.exists(dbPath)) {
                createEmptyDatabase(dbPath.toString());
                log.info("Created empty SQLite database as fallback: {}", dbPath);
            }

        } catch (Exception e) {
            log.warn("Failed to ensure database path exists: {}. Database operations may fail.", e.getMessage());
        }
    }

    /**
     * Create empty SQLite database with basic structure
     */
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

            log.debug("Created empty tags table structure in fallback database");
        }
    }

    /**
     * Verify that downloaded data is accessible and contains records
     */
    private void verifyDownloadedData() {
        try {
            log.info("Verifying downloaded SQLite database...");

            // Test direct SQL connection to the downloaded file
            String url = "jdbc:sqlite:" + sqliteProperties.getLocalPath();
            try (Connection conn = DriverManager.getConnection(url);
                 Statement stmt = conn.createStatement()) {

                // Count total records directly from SQLite
                var rs = stmt.executeQuery("SELECT COUNT(*) as count FROM tags");
                if (rs.next()) {
                    int recordCount = rs.getInt("count");
                    log.info("Downloaded database contains {} records (direct SQL check)", recordCount);

                    if (recordCount == 0) {
                        log.warn("Downloaded database appears to be empty!");
                    }
                } else {
                    log.warn("Could not verify record count in downloaded database");
                }

                // Count distinct tagfiles
                rs = stmt.executeQuery("SELECT COUNT(DISTINCT tagfile) as count FROM tags");
                if (rs.next()) {
                    int tagfileCount = rs.getInt("count");
                    log.info("Downloaded database contains {} distinct tagfiles", tagfileCount);
                }

            } catch (SQLException sqlException) {
                log.error("Failed to verify downloaded data via direct SQL: {}", sqlException.getMessage());
            }

            // Also test through JPA repository (might be cached)
            try {
                List<String> tagfiles = waveTagRepository.findDistinctTagfiles();
                log.info("JPA repository reports {} tagfiles (may be cached)", tagfiles.size());

                if (tagfiles.isEmpty()) {
                    log.warn("JPA repository shows no tagfiles - possible connection cache issue");
                }

            } catch (Exception jpaException) {
                log.warn("JPA verification failed: {}", jpaException.getMessage());
            }

        } catch (Exception e) {
            log.error("Error verifying downloaded data: {}", e.getMessage(), e);
        }
    }
}
