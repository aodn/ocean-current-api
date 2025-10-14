package au.org.aodn.oceancurrent.service.tags;

import au.org.aodn.oceancurrent.configuration.remoteJson.RemoteServiceProperties;
import au.org.aodn.oceancurrent.configuration.sqlite.SqliteProperties;
import au.org.aodn.oceancurrent.dto.WaveTagResponse;
import au.org.aodn.oceancurrent.service.SqliteBaseService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class SurfaceWavesTagService extends SqliteBaseService implements ProductTagService {

    private static final String PRODUCT_TYPE = "surface-waves";

    public SurfaceWavesTagService(RemoteServiceProperties remoteProperties, SqliteProperties sqliteProperties) {
        super(remoteProperties, sqliteProperties);
    }

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
    public boolean ensureDataAvailability() {
        return super.ensureDataAvailability();
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

    /**
     * Check if the database has the required surface waves tag data
     */
    @Override
    protected boolean hasRequiredData() {
        try {
            // Check if the tags table exists and has data
            try (Connection conn = getConnection();
                 Statement stmt = conn.createStatement()) {

                // Check if tags table exists
                try {
                    ResultSet rs = stmt.executeQuery("SELECT COUNT(*) as count FROM tags");
                    if (rs.next()) {
                        int count = rs.getInt("count");
                        return count > 0; // Table exists and has data
                    }
                } catch (SQLException e) {
                    log.debug("Tags table does not exist or cannot be queried: {}", e.getMessage());
                    return false;
                }
            }
            return false;
        } catch (Exception e) {
            log.debug("Error checking if database has required data: {}", e.getMessage());
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
     * Get all tag files using direct SQL (bypasses JPA cache)
     */
    private List<String> getAllTagFilesDirectSql() throws SQLException {
        List<String> tagFiles = new ArrayList<>();

        try (Connection conn = getConnection();
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
        List<WaveTagResponse.TagData> tags = new ArrayList<>();

        try (Connection conn = getConnection();
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
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT 1 FROM tags WHERE tagfile = ? LIMIT 1")) {

            stmt.setString(1, tagFile);
            ResultSet rs = stmt.executeQuery();
            return rs.next(); // Returns true if at least one record exists
        }
    }

    /**
     * Perform additional verification specific to SurfaceWavesTagService
     */
    @Override
    protected void performAdditionalVerification(Connection conn, Statement stmt) throws SQLException {
        // Count total records directly from SQLite
        var rs = stmt.executeQuery("SELECT COUNT(*) as count FROM tags");
        if (rs.next()) {
            int recordCount = rs.getInt("count");
            log.info("Downloaded database contains {} tag records", recordCount);

            if (recordCount == 0) {
                log.warn("Downloaded database appears to have no tag records!");
            }
        } else {
            log.warn("Could not verify tag record count in downloaded database");
        }

        // Count distinct tag files
        rs = stmt.executeQuery("SELECT COUNT(DISTINCT tagfile) as count FROM tags");
        if (rs.next()) {
            int tagFileCount = rs.getInt("count");
            log.info("Downloaded database contains {} distinct tag files", tagFileCount);
        }
    }
}
