package au.org.aodn.oceancurrent.service;

import au.org.aodn.oceancurrent.configuration.sqlite.SqliteProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.InputStream;
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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Base service for SQLite database operations
 * This class provides common functionality for services that use SQLite database
 */
@Slf4j
@RequiredArgsConstructor
public abstract class SqliteBaseService {

    // Static lock to prevent concurrent downloads
    private static final ReentrantLock downloadLock = new ReentrantLock();

    // Flag to track if download is in progress
    private static boolean downloadInProgress = false;

    protected final SqliteProperties sqliteProperties;

    /**
     * Get a JDBC connection to the SQLite database
     *
     * @return Connection object
     * @throws SQLException if the connection fails
     */
    protected Connection getConnection() throws SQLException {
        String url = "jdbc:sqlite:" + sqliteProperties.getLocalPath();
        return DriverManager.getConnection(url);
    }

    /**
     * Check if the SQLite database file exists locally and is accessible
     */
    public boolean isDatabaseAvailable() {
        try {
            // Ensure the directory and basic file structure exist first
            ensureDatabasePathExists();

            Path dbPath = Paths.get(sqliteProperties.getLocalPath()).toAbsolutePath();
            if (!Files.exists(dbPath) || !Files.isRegularFile(dbPath)) {
                log.debug("SQLite database file does not exist: {}", dbPath);
                return false;
            }

            // Test database connectivity
            try (Connection conn = getConnection();
                 Statement stmt = conn.createStatement()) {
                var rs = stmt.executeQuery("SELECT 1");
                if (rs.next()) {
                    log.debug("Database is available");
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
     * Download SQLite database from the remote server
     * This method is synchronized to prevent multiple services from downloading simultaneously
     */
    protected boolean downloadSqliteDatabase() {
        // Use a lock to prevent concurrent downloads from multiple services
        if (!downloadLock.tryLock()) {
            log.info("Another download is already in progress, waiting...");
            try {
                downloadLock.lock();
                // If we get here, the other download has finished
                if (isDatabaseAvailable()) {
                    log.info("Database was downloaded by another service, no need to download again");
                    return true;
                }
            } finally {
                downloadLock.unlock();
            }
        }

        try {
            downloadLock.lock();
            downloadInProgress = true;

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

            // Create a directory if it doesn't exist
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
                if (fileSize < 1024) { // Less than 1KB suggests an empty or corrupt file
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
        } finally {
            downloadInProgress = false;
            downloadLock.unlock();
        }
    }

    /**
     * Ensure the database directory and empty file exist as fallback
     */
    protected void ensureDatabasePathExists() {
        try {
            String localPath = sqliteProperties.getLocalPath();
            Path dbPath = Paths.get(localPath).toAbsolutePath();
            Path parentDir = dbPath.getParent();

            // Create a directory structure if needed
            if (parentDir != null && !Files.exists(parentDir)) {
                Files.createDirectories(parentDir);
                log.info("Created SQLite directory as fallback: {}", parentDir);
            }

            // Create an empty database file if needed
            if (!Files.exists(dbPath)) {
                createEmptyDatabase();
                log.info("Created empty SQLite database as fallback: {}", dbPath);
            }

        } catch (Exception e) {
            log.warn("Failed to ensure database path exists: {}. Database operations may fail.", e.getMessage());
        }
    }

    /**
     * Create an empty SQLite database as a placeholder
     * This is just a minimal database that will be replaced after downloading
     */
    protected void createEmptyDatabase() throws SQLException {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {

            // Create a simple placeholder table
            String createPlaceholderTableSql = """
                    CREATE TABLE IF NOT EXISTS placeholder (
                        id INTEGER PRIMARY KEY,
                        info TEXT
                    );
                    """;

            stmt.execute(createPlaceholderTableSql);

            // Insert a record to indicate this is a placeholder
            stmt.execute("INSERT INTO placeholder (id, info) VALUES (1, 'Placeholder database - will be replaced on download')");

            log.debug("Created minimal placeholder database");
        }
    }

    /**
     * Verify that downloaded data is accessible and contains records
     * This implementation checks common tables used by all subclasses
     * Subclasses can override performAdditionalVerification() to add their specific verification logic
     */
    protected void verifyDownloadedData() {
        try {
            log.info("Verifying downloaded SQLite database...");

            try (Connection conn = getConnection();
                 Statement stmt = conn.createStatement()) {

                // Count total records in the `region_product` table
                var rpRs = stmt.executeQuery("SELECT COUNT(*) as count FROM region_product");
                if (rpRs.next()) {
                    int recordCount = rpRs.getInt("count");
                    log.info("Downloaded database contains {} region_product records", recordCount);
                }

                // Count total records in the `files` table
                var filesRs = stmt.executeQuery("SELECT COUNT(*) as count FROM files");
                if (filesRs.next()) {
                    int recordCount = filesRs.getInt("count");
                    log.info("Downloaded database contains {} files records", recordCount);

                    if (recordCount == 0) {
                        log.warn("Downloaded database appears to have no files records!");
                    }
                }

                // Perform any additional verification provided by subclasses
                performAdditionalVerification(conn, stmt);

            } catch (SQLException sqlException) {
                log.error("Failed to verify downloaded data via SQL: {}", sqlException.getMessage());
            }

        } catch (Exception e) {
            log.error("Error verifying downloaded data: {}", e.getMessage(), e);
        }
    }

    /**
     * Check if a download is currently in progress
     */
    public static boolean isDownloadInProgress() {
        return downloadInProgress;
    }

    /**
     * Perform additional verification specific to a subclass
     * Override this method to add custom verification logic
     *
     * @param conn The database connection
     * @param stmt The statement object for executing SQL
     */
    protected void performAdditionalVerification(Connection conn, Statement stmt) throws SQLException {
        // Default implementation does nothing
    }
}
