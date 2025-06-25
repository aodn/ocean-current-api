package au.org.aodn.oceancurrent.controller;

import au.org.aodn.oceancurrent.dto.ErrorResponse;
import au.org.aodn.oceancurrent.dto.WaveTagResponse;
import au.org.aodn.oceancurrent.service.TagService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/tags")
@Tag(name = "Product Tags", description = "API for managing and querying product tag data from various sources")
public class TagController {

    private final TagService tagService;

    @Autowired
    public TagController(TagService tagService) {
        this.tagService = tagService;
    }

        @GetMapping("/surface-waves/by-date/{dateTime}")
    @Operation(summary = "Get surface wave tags by date",
               description = "Retrieve all surface wave tag data for a specific date (e.g., 2021010100)")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved wave tags"),
        @ApiResponse(responseCode = "404", description = "Date not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<?> getWaveTagsByDate(
            @Parameter(description = "Date in format YYYYMMDDHH (e.g., 2021010100)", required = true)
            @PathVariable String dateTime) {

        try {
            log.info("Querying wave tags for date: {}", dateTime);

            // Ensure data is available (auto-download if needed)
            if (!ensureDataAvailable()) {
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(new ErrorResponse("Surface wave data is temporarily unavailable. Please try again in a moment."));
            }

            // Construct tagfile from date pattern: y2021/m01/2021010100_Buoy.txt
            String tagfile = constructTagfilePath(dateTime);

            if (!tagService.tagfileExists(tagfile)) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponse("No data found for date '" + dateTime + "'"));
            }

            WaveTagResponse response = tagService.getTagsByTagfile(tagfile);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error retrieving wave tags for date {}: {}", dateTime, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("Error retrieving wave tags: " + e.getMessage()));
        }
    }

    @GetMapping("/surface-waves/by-tagfile")
    @Operation(summary = "Get surface wave tags by full tagfile path",
               description = "Retrieve all surface wave tag data using complete tagfile path (use query parameter)")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved wave tags"),
        @ApiResponse(responseCode = "404", description = "Tagfile not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<?> getWaveTagsByTagfile(
            @Parameter(description = "Complete tagfile path", required = true)
            @RequestParam String tagfile) {

        try {
            log.info("Querying wave tags for tagfile: {}", tagfile);

            // Ensure data is available (auto-download if needed)
            if (!ensureDataAvailable()) {
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(new ErrorResponse("Surface wave data is temporarily unavailable. Please try again in a moment."));
            }

            if (!tagService.tagfileExists(tagfile)) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponse("Tagfile '" + tagfile + "' not found in database"));
            }

            WaveTagResponse response = tagService.getTagsByTagfile(tagfile);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error retrieving wave tags for tagfile {}: {}", tagfile, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("Error retrieving wave tags: " + e.getMessage()));
        }
    }

    @GetMapping("/surface-waves/tagfiles")
    @Operation(summary = "Get all available surface wave tagfiles",
               description = "Retrieve a list of all available surface wave tagfiles in the database")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved tagfiles"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<?> getAllTagfiles() {
        try {
            log.info("Retrieving all available tagfiles");

            // Ensure data is available (auto-download if needed)
            if (!ensureDataAvailable()) {
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(new ErrorResponse("Surface wave data is temporarily unavailable. Please try again in a moment."));
            }

            List<String> tagfiles = tagService.getAllTagfiles();
            return ResponseEntity.ok(Map.of("tagfiles", tagfiles, "count", tagfiles.size()));

        } catch (Exception e) {
            log.error("Error retrieving tagfiles: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("Error retrieving tagfiles: " + e.getMessage()));
        }
    }

    @PostMapping("/surface-waves/download")
    @Operation(summary = "Trigger manual surface wave data download",
               description = "Manually trigger the download of surface wave SQLite database from remote server")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Download completed successfully"),
        @ApiResponse(responseCode = "500", description = "Download failed")
    })
    public ResponseEntity<?> triggerDownload() {
        try {
            log.info("Manual download trigger requested");

            boolean success = tagService.downloadSqliteDatabase();

            if (success) {
                int tagfileCount = 0;
                try {
                    tagfileCount = tagService.getAllTagfiles().size();
                } catch (Exception e) {
                    log.debug("Error getting tagfile count after download: {}", e.getMessage());
                }

                return ResponseEntity.ok(Map.of(
                    "message", "Surface wave data downloaded successfully",
                    "status", "success",
                    "tagfileCount", tagfileCount
                ));
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to download surface wave data. Please try again later."));
            }

        } catch (Exception e) {
            log.error("Error during manual download: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("Error downloading surface wave data: " + e.getMessage()));
        }
    }

    @GetMapping("/surface-waves/status")
    @Operation(summary = "Get surface wave database status",
               description = "Check if the local surface wave SQLite database is available and get basic statistics")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Status retrieved successfully")
    })
    public ResponseEntity<?> getDatabaseStatus() {
        try {
            boolean available = tagService.isDatabaseAvailable();
            int tagfileCount = 0;
            String lastUpdate = "unknown";

            if (available) {
                try {
                    tagfileCount = tagService.getAllTagfiles().size();
                    lastUpdate = "recently"; // You could add timestamp tracking in the future
                } catch (Exception e) {
                    log.debug("Error getting tagfile count: {}", e.getMessage());
                }
            }

            Map<String, Object> status = Map.of(
                "databaseAvailable", available,
                "tagfileCount", tagfileCount,
                "lastUpdate", lastUpdate,
                "message", available ?
                    String.format("Surface wave data is available (%d tagfiles)", tagfileCount) :
                    "Surface wave data is not available",
                "autoDownload", "enabled"
            );

            return ResponseEntity.ok(status);

        } catch (Exception e) {
            log.error("Error checking database status: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("Error checking database status: " + e.getMessage()));
        }
    }

    /**
     * Ensure data is available, auto-download if needed
     * @return true if data is available, false if still unavailable after download attempt
     */
    private boolean ensureDataAvailable() {
        try {
            // Quick check if data is already available
            if (tagService.isDatabaseAvailable()) {
                return true;
            }

            // Data not available, attempt auto-download
            log.info("Database not available, attempting automatic download...");
            boolean downloadSuccess = tagService.downloadSqliteDatabase();

            if (downloadSuccess) {
                log.info("Automatic download completed successfully");
                return tagService.isDatabaseAvailable();
            } else {
                log.warn("Automatic download failed");
                return false;
            }
        } catch (Exception e) {
            log.error("Error ensuring data availability: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Construct tagfile path from date string
     * Example: 2021010100 -> y2021/m01/2021010100_Buoy.txt
     */
    private String constructTagfilePath(String dateTime) {
        if (dateTime == null || dateTime.length() != 10) {
            throw new IllegalArgumentException("Date must be in format YYYYMMDDHH (10 digits)");
        }

        String year = dateTime.substring(0, 4);
        String month = dateTime.substring(4, 6);

        return String.format("y%s/m%s/%s_Buoy.txt", year, month, dateTime);
    }
}
