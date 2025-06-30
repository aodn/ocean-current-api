package au.org.aodn.oceancurrent.service;

import au.org.aodn.oceancurrent.configuration.sqlite.SqliteProperties;
import au.org.aodn.oceancurrent.dto.WaveTagResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SurfaceWavesTagService implements ProductTagService {

    private static final String PRODUCT_TYPE = "surface-waves";

    private final SqliteProperties sqliteProperties;

    @Override
    public String getProductType() {
        return PRODUCT_TYPE;
    }

    @Override
    public boolean downloadData() {
        return downloadSqliteDatabase();
    }

    @Override
    public boolean isDataAvailable() {
        return isDatabaseAvailable();
    }

    @Override
    public boolean hasData() {
        try {
            return !getAllTagFiles().isEmpty();
        } catch (Exception e) {
            log.debug("Error checking if database has data: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public List<String> getAllTagFiles() {
        try {
            log.debug("Getting all tag files using direct SQL");
            return getAllTagFilesDirectSql();
        } catch (Exception e) {
            log.error("Error getting all tag files: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to get tag files", e);
        }
    }

    @Override
    public boolean tagFileExists(String tagFile) {
        try {
            log.debug("Checking if tag file {} exists using direct SQL", tagFile);
            return tagFileExistsDirectSql(tagFile);
        } catch (Exception e) {
            log.error("Error checking if tag file exists: {}", e.getMessage(), e);
            return false;
        }
    }

    @Override
    public Object getTagsByTagFile(String tagFile) {
        try {
            log.debug("Querying tags for tag file {} using direct SQL", tagFile);
            List<WaveTagResponse.TagData> tags = getTagsByTagFileDirectSql(tagFile);
            return new WaveTagResponse(tagFile, tags);
        } catch (Exception e) {
            log.error("Error querying tags for tag file {}: {}", tagFile, e.getMessage(), e);
            throw new RuntimeException("Failed to query tags for tag file: " + tagFile, e);
        }
    }

    @Override
    public String constructTagFilePath(String dateTime) {
        if (!isValidDateFormat(dateTime)) {
            throw new IllegalArgumentException("Date must be in format YYYYMMDDHH (10 digits)");
        }

        String year = dateTime.substring(0, 4);
        String month = dateTime.substring(4, 6);

        return String.format("y%s/m%s/%s_Buoy.txt", year, month, dateTime);
    }

    @Override
    public boolean isValidDateFormat(String dateTime) {
        return dateTime != null && dateTime.length() == 10 && dateTime.matches("\\d{10}");
    }

    /**
     * Download SQLite database from remote server
     */
    private boolean downloadSqliteDatabase() {
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

            // Give the file system a moment to flush
            Thread.sleep(1000); // 1 second delay

            // Verify the download worked
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
     * Check if SQLite database file exists locally and is accessible
     */
    private boolean isDatabaseAvailable() {
        try {
            // Ensure directory and basic file structure exist first
            ensureDatabasePathExists();

            Path dbPath = Paths.get(sqliteProperties.getLocalPath()).toAbsolutePath();
            if (!Files.exists(dbPath) || !Files.isRegularFile(dbPath)) {
                log.debug("SQLite database file does not exist: {}", dbPath);
                return false;
            }

            // Test database connectivity with direct SQL
            String url = "jdbc:sqlite:" + sqliteProperties.getLocalPath();
            try (Connection conn = DriverManager.getConnection(url);
                 Statement stmt = conn.createStatement()) {
                var rs = stmt.executeQuery("SELECT COUNT(DISTINCT tagfile) as count FROM tags");
                if (rs.next()) {
                    int tagFileCount = rs.getInt("count");
                    log.debug("Database is available with {} tag files", tagFileCount);
                    return true;
                }
            } catch (SQLException sqlEx) {
                log.debug("Database connectivity test failed: {}", sqlEx.getMessage());
                return false;
            }

            return false;
        } catch (Exception e) {
            log.debug("Database connectivity test failed: {}", e.getMessage());
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
                    log.info("Downloaded database contains {} records", recordCount);

                    if (recordCount == 0) {
                        log.warn("Downloaded database appears to be empty!");
                    }
                } else {
                    log.warn("Could not verify record count in downloaded database");
                }

                // Count distinct tag files
                rs = stmt.executeQuery("SELECT COUNT(DISTINCT tagfile) as count FROM tags");
                if (rs.next()) {
                    int tagFileCount = rs.getInt("count");
                    log.info("Downloaded database contains {} distinct tag files", tagFileCount);
                }

            } catch (SQLException sqlException) {
                log.error("Failed to verify downloaded data via direct SQL: {}", sqlException.getMessage());
            }

        } catch (Exception e) {
            log.error("Error verifying downloaded data: {}", e.getMessage(), e);
        }
    }

    /**
     * Get all tag files using direct SQL (bypasses JPA cache)
     */
    private List<String> getAllTagFilesDirectSql() throws SQLException {
        String url = "jdbc:sqlite:" + sqliteProperties.getLocalPath();
        List<String> tagFiles = new ArrayList<>();

        try (Connection conn = DriverManager.getConnection(url);
             Statement stmt = conn.createStatement()) {

            var rs = stmt.executeQuery("SELECT DISTINCT tagfile FROM tags ORDER BY tagfile");
            while (rs.next()) {
                tagFiles.add(rs.getString("tagfile"));
            }
        }

        return tagFiles;
    }

    /**
     * Get tags for a specific tag file using direct SQL (bypasses JPA cache)
     */
    private List<WaveTagResponse.TagData> getTagsByTagFileDirectSql(String tagFile) throws SQLException {
        String url = "jdbc:sqlite:" + sqliteProperties.getLocalPath();
        List<WaveTagResponse.TagData> tags = new ArrayList<>();

        try (Connection conn = DriverManager.getConnection(url);
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT x, y, sz, title, url FROM tags WHERE tagfile = ? ORDER BY \"order\"")) {

            stmt.setString(1, tagFile);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                tags.add(new WaveTagResponse.TagData(
                        rs.getInt("x"),
                        rs.getInt("y"),
                        rs.getInt("sz"),
                        rs.getString("title"),
                        rs.getString("url")
                ));
            }
        }

        return tags;
    }

    /**
     * Check if tag file exists using direct SQL (bypasses JPA cache)
     */
    private boolean tagFileExistsDirectSql(String tagFile) throws SQLException {
        String url = "jdbc:sqlite:" + sqliteProperties.getLocalPath();

        try (Connection conn = DriverManager.getConnection(url);
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT 1 FROM tags WHERE tagfile = ? LIMIT 1")) {

            stmt.setString(1, tagFile);
            ResultSet rs = stmt.executeQuery();
            return rs.next(); // Returns true if at least one record exists
        }
    }
}
