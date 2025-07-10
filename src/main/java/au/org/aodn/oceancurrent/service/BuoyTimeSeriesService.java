package au.org.aodn.oceancurrent.service;

import au.org.aodn.oceancurrent.configuration.sqlite.SqliteProperties;
import au.org.aodn.oceancurrent.constant.CacheNames;
import au.org.aodn.oceancurrent.model.ImageMetadataEntry;
import au.org.aodn.oceancurrent.model.ImageMetadataGroup;
import au.org.aodn.oceancurrent.util.converter.ImageMetadataConverter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Service for handling buoy time series data from SQLite database
 */
@Slf4j
@Service
public class BuoyTimeSeriesService extends SqliteBaseService {

    public BuoyTimeSeriesService(SqliteProperties sqliteProperties) {
        super(sqliteProperties);
    }

    /**
     * Find all buoy time series image files for a specific region
     *
     * @param region The region name
     * @return List of ImageMetadataGroup containing the image files
     */
    @Cacheable(value = CacheNames.BUOY_TIME_SERIES, key = "{#region}")
    public List<ImageMetadataGroup> findAllBuoyTimeSeriesByRegion(String region) {
        log.info("Finding buoy time series image files for region: {}", region);

        // Ensure data is available, download if needed
        if (!ensureDataAvailability()) {
            log.error("Failed to ensure data availability for buoy time series");
            return List.of();
        }

        try {
            List<ImageMetadataEntry> entries = new ArrayList<>();

            try (Connection conn = getConnection()) {
                // First get the rpid for the region and product type 'tsimage'
                String rpidQuery = "SELECT rpid FROM region_product WHERE region = ? AND product = 'tsimage'";

                try (PreparedStatement rpidStmt = conn.prepareStatement(rpidQuery)) {
                    rpidStmt.setString(1, region);
                    ResultSet rpidRs = rpidStmt.executeQuery();

                    if (rpidRs.next()) {
                        int rpid = rpidRs.getInt("rpid");

                        // Now get all files for this rpid
                        String filesQuery = "SELECT file_name, date FROM files WHERE rpid = ? ORDER BY date";

                        try (PreparedStatement filesStmt = conn.prepareStatement(filesQuery)) {
                            filesStmt.setInt(1, rpid);
                            ResultSet filesRs = filesStmt.executeQuery();

                            while (filesRs.next()) {
                                String fileName = filesRs.getString("file_name");
                                String date = filesRs.getString("date");

                                // Extract date from the file name or date field and format it
                                String simplifiedFileName = extractSimpleDateFromFileName(fileName, date);

                                // Create path as requested
                                String path = "WAVES_TS";

                                // Create ImageMetadataEntry
                                ImageMetadataEntry entry = new ImageMetadataEntry();
                                entry.setFileName(simplifiedFileName);
                                entry.setPath(path);
                                entry.setProductId(null); // Set productId to null as requested
                                entry.setRegion(region);

                                entries.add(entry);
                            }
                        }
                    } else {
                        log.warn("No rpid found for region: {} and product: tsimage", region);
                    }
                }
            }

            log.info("Found {} buoy time series image files for region: {}", entries.size(), region);

            return ImageMetadataConverter.createMetadataGroups(entries);

        } catch (Exception e) {
            log.error("Error querying buoy time series data from SQLite: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to retrieve buoy time series data", e);
        }
    }

    /**
     * Check if the database has the required buoy time series data
     */
    @Override
    protected boolean hasRequiredData() {
        try {
            try (Connection conn = getConnection();
                 Statement stmt = conn.createStatement()) {

                // Check if region_product table has tsimage entries
                try {
                    ResultSet rs = stmt.executeQuery("SELECT COUNT(*) as count FROM region_product WHERE product = 'tsimage'");
                    if (rs.next()) {
                        int count = rs.getInt("count");
                        if (count == 0) {
                            log.debug("No tsimage entries found in region_product table");
                            return false;
                        }
                    }
                } catch (SQLException e) {
                    log.debug("region_product table does not exist or cannot be queried: {}", e.getMessage());
                    return false;
                }

                // Check if files table has data for any tsimage rpid
                try {
                    ResultSet rs = stmt.executeQuery("""
                            SELECT COUNT(*) as count FROM files
                            WHERE rpid IN (SELECT rpid FROM region_product WHERE product = 'tsimage')
                            """);
                    if (rs.next()) {
                        int count = rs.getInt("count");
                        if (count == 0) {
                            log.debug("No files found for tsimage product");
                            return false;
                        }
                        return true;
                    }
                } catch (SQLException e) {
                    log.debug("files table does not exist or cannot be queried: {}", e.getMessage());
                    return false;
                }
            }
            return false;
        } catch (Exception e) {
            log.debug("Error checking if database has required data: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Extract a simplified date string from file name or date field
     * Format should be like "2024060100.png"
     */
    private String extractSimpleDateFromFileName(String fileName, String date) {
        // Log the input for debugging
        log.debug("Extracting date from fileName: {} and date: {}", fileName, date);

        // Try to extract date from fileName first
        if (fileName != null && !fileName.isEmpty()) {
            // Check for patterns like "Gold Coast/y2024/m06/20240601T0000_BuoyTS.png"
            if (fileName.matches(".*/y\\d{4}/m\\d{2}/\\d{8}T\\d{4}_BuoyTS\\.png")) {
                // Extract the date part (20240601T0000)
                String datePattern = fileName.replaceAll(".*/y\\d{4}/m\\d{2}/(\\d{8}T\\d{4})_BuoyTS\\.png", "$1");
                // Convert to YYYYMMDDHH format
                String year = datePattern.substring(0, 4);
                String month = datePattern.substring(4, 6);
                String day = datePattern.substring(6, 8);
                String hour = datePattern.substring(9, 11);

                String result = year + month + day + hour + ".png";
                log.debug("Extracted date from fileName pattern 1: {}", result);
                return result;
            }
            // Check for other date patterns in file name
            else if (fileName.matches(".*\\d{8}T\\d{4}.*")) {
                // Extract YYYYMMDDTHHMM pattern
                String datePattern = fileName.replaceAll(".*?(\\d{8}T\\d{4}).*", "$1");
                // Convert to YYYYMMDDHH format
                String year = datePattern.substring(0, 4);
                String month = datePattern.substring(4, 6);
                String day = datePattern.substring(6, 8);
                String hour = datePattern.substring(9, 11);

                String result = year + month + day + hour + ".png";
                log.debug("Extracted date from fileName pattern 2: {}", result);
                return result;
            } else if (fileName.matches(".*\\d{8}.*")) {
                // Extract YYYYMMDD pattern
                String datePattern = fileName.replaceAll(".*?(\\d{8}).*", "$1");
                String result = datePattern + "00.png"; // Add default hour and extension
                log.debug("Extracted date from fileName pattern 3: {}", result);
                return result;
            }
        }

        // Fall back to date field if available
        if (date != null && !date.isEmpty()) {
            try {
                // Parse the date which might be in format like "2024-06-01 00:00:00"
                String[] parts = date.split("[ T:-]");
                if (parts.length >= 6) {
                    String year = parts[0];
                    String month = parts[1];
                    String day = parts[2];
                    String hour = parts[3];

                    String result = year + month + day + hour + ".png";
                    log.debug("Extracted date from date field: {}", result);
                    return result;
                }

                // Alternative approach if the above doesn't work
                String cleanDate = date.replaceAll("[^0-9]", "");
                if (cleanDate.length() >= 10) {
                    String result = cleanDate.substring(0, 10) + ".png";
                    log.debug("Extracted date using alternative approach: {}", result);
                    return result;
                }
            } catch (Exception e) {
                log.warn("Error parsing date: {}", e.getMessage());
            }
        }

        // If all else fails, return a placeholder
        log.warn("Could not extract date from fileName: {} or date: {}", fileName, date);
        return "unknown.png";
    }
}
