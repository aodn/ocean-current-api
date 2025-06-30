package au.org.aodn.oceancurrent.controller;

import au.org.aodn.oceancurrent.dto.ErrorResponse;
import au.org.aodn.oceancurrent.dto.WaveTagResponse;
import au.org.aodn.oceancurrent.service.tags.TagService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
@Tag(name = "Product Tags", description = "API for managing and querying product tag data from various sources")
public class TagController {

    private final TagService tagService;

    @GetMapping("/surface-waves/by-date/{dateTime}")
    @Operation(summary = "Get surface wave buoy tags by date",
            description = "Retrieve all surface wave buoy tag data for a specific date (e.g., 2021010100)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved wave tags"),
            @ApiResponse(responseCode = "404", description = "Date not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<?> getWaveTagsByDate(
            @Parameter(description = "Date in format YYYYMMDDHH (e.g., 2021010100)", required = true)
            @PathVariable String dateTime) {

        try {
            log.info("Querying surface wave buoy tags for date: {}", dateTime);

            // Ensure data is available (auto-download if needed)
            if (!ensureDataAvailable()) {
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .body(new ErrorResponse("Surface wave buoy data is temporarily unavailable. Please try again in a moment."));
            }

            // Construct tag file from date pattern: y2021/m01/2021010100_Buoy.txt
            String tagFile = tagService.constructTagFilePath("surface-waves", dateTime);
            log.info("Constructed tag file path for date {}: {}", dateTime, tagFile);

            if (!tagService.tagFileExists("surface-waves", tagFile)) {
                log.warn("Tag file {} does not exist for date {}", tagFile, dateTime);

                // Help debug by showing available tag files for this date pattern
                try {
                    List<String> allTagFiles = tagService.getAllTagFiles("surface-waves");
                    String datePrefix = tagFile.substring(0, tagFile.lastIndexOf("/") + 1); // e.g., "y2021/m01/"
                    List<String> similarTagFiles = allTagFiles.stream()
                            .filter(tf -> tf.startsWith(datePrefix))
                            .limit(5)
                            .collect(java.util.stream.Collectors.toList());

                    if (!similarTagFiles.isEmpty()) {
                        log.info("Available tag files for similar date pattern {}: {}", datePrefix, similarTagFiles);
                    } else {
                        log.info("No tag files found for date pattern {}", datePrefix);
                    }
                } catch (Exception debugException) {
                    log.debug("Could not retrieve tag files for debugging: {}", debugException.getMessage());
                }

                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ErrorResponse("No data found for date '" + dateTime + "'. Expected tag file: " + tagFile));
            }

            Object responseObj = tagService.getTagsByTagFile("surface-waves", tagFile);
            if (responseObj instanceof WaveTagResponse response) {
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(new ErrorResponse("Unexpected response type for surface waves data"));
            }

        } catch (Exception e) {
            log.error("Error retrieving wave tags for date {}: {}", dateTime, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Error retrieving wave tags: " + e.getMessage()));
        }
    }

    @GetMapping("/surface-waves/by-tag-file")
    @Operation(summary = "Get surface wave tags by full tag file path",
            description = "Retrieve all surface wave tag data using complete tag file path (use query parameter)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved wave tags"),
            @ApiResponse(responseCode = "404", description = "Tag file not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<?> getWaveTagsByTagFile(
            @Parameter(description = "Complete tag file path", required = true)
            @RequestParam String tagFile) {

        try {
            log.info("Querying wave tags for tag file: {}", tagFile);

            // Ensure data is available (auto-download if needed)
            if (!ensureDataAvailable()) {
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .body(new ErrorResponse("Surface wave data is temporarily unavailable. Please try again in a moment."));
            }

            if (!tagService.tagFileExists("surface-waves", tagFile)) {
                log.warn("Tag file {} not found in database", tagFile);
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ErrorResponse("Tag file '" + tagFile + "' not found in database"));
            }

            Object responseObj = tagService.getTagsByTagFile("surface-waves", tagFile);
            if (responseObj instanceof WaveTagResponse response) {
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(new ErrorResponse("Unexpected response type for surface waves data"));
            }

        } catch (Exception e) {
            log.error("Error retrieving wave tags for tag file {}: {}", tagFile, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Error retrieving wave tags: " + e.getMessage()));
        }
    }

    @GetMapping("/surface-waves/tag-files")
    @Operation(summary = "Get all available surface wave tag files",
            description = "Retrieve a list of all available surface wave tag files in the database")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved tag files"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<?> getAllTagFiles() {
        try {
            log.info("Retrieving all available tag files");

            // Ensure data is available (auto-download if needed)
            if (!ensureDataAvailable()) {
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .body(new ErrorResponse("Surface wave data is temporarily unavailable. Please try again in a moment."));
            }

            List<String> tagFiles = tagService.getAllTagFiles("surface-waves");
            return ResponseEntity.ok(Map.of("tagfiles", tagFiles, "count", tagFiles.size()));

        } catch (Exception e) {
            log.error("Error retrieving tag files: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Error retrieving tag files: " + e.getMessage()));
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

            boolean success = tagService.downloadData("surface-waves");

            if (success) {
                int tagFileCount = 0;
                try {
                    tagFileCount = tagService.getAllTagFiles("surface-waves").size();
                } catch (Exception e) {
                    log.debug("Error getting tag file count after download: {}", e.getMessage());
                }

                return ResponseEntity.ok(Map.of(
                        "message", "Surface wave data downloaded successfully",
                        "status", "success",
                        "tagFileCount", tagFileCount
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
            boolean available = tagService.isDataAvailable("surface-waves");
            int tagFileCount = 0;
            String lastUpdate = "unknown";

            if (available) {
                try {
                    tagFileCount = tagService.getAllTagFiles("surface-waves").size();
                    lastUpdate = "recently"; // You could add timestamp tracking in the future
                } catch (Exception e) {
                    log.debug("Error getting tag file count: {}", e.getMessage());
                }
            }

            Map<String, Object> status = Map.of(
                    "databaseAvailable", available,
                    "tagFileCount", tagFileCount,
                    "lastUpdate", lastUpdate,
                    "message", available ?
                            String.format("Surface wave data is available (%d tag files)", tagFileCount) :
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

    // Generic endpoints for all product types

    @GetMapping("/products")
    @Operation(summary = "Get all supported product types",
            description = "Retrieve a list of all supported product types for tag data")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved supported product types")
    })
    public ResponseEntity<?> getSupportedProductTypes() {
        try {
            List<String> productTypes = tagService.getSupportedProductTypes();
            return ResponseEntity.ok(Map.of(
                    "productTypes", productTypes,
                    "count", productTypes.size()
            ));
        } catch (Exception e) {
            log.error("Error retrieving supported product types: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Error retrieving supported product types: " + e.getMessage()));
        }
    }

    @GetMapping("/{productType}/by-date/{dateTime}")
    @Operation(summary = "Get tags by date for any product type",
            description = "Retrieve tag data for a specific date and product type")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved tags"),
            @ApiResponse(responseCode = "404", description = "Date or product not found"),
            @ApiResponse(responseCode = "400", description = "Invalid product type or date format"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<?> getTagsByDate(
            @Parameter(description = "Product type (e.g., surface-waves)", required = true)
            @PathVariable String productType,
            @Parameter(description = "Date (format depends on product type)", required = true)
            @PathVariable String dateTime) {

        try {
            log.info("Querying tags for product {} and date: {}", productType, dateTime);

            // Validate product type
            if (!tagService.getSupportedProductTypes().contains(productType)) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new ErrorResponse("Unsupported product type: " + productType));
            }

            // Validate date format for this product
            if (!tagService.isValidDateFormat(productType, dateTime)) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new ErrorResponse("Invalid date format for product type " + productType + ": " + dateTime));
            }

            // Ensure data is available
            if (!tagService.isDataAvailable(productType) || !tagService.hasData(productType)) {
                // Try to download data
                boolean downloadSuccess = tagService.downloadData(productType);
                if (!downloadSuccess || !tagService.isDataAvailable(productType)) {
                    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                            .body(new ErrorResponse("Data for product " + productType + " is temporarily unavailable. Please try again in a moment."));
                }
            }

            // Construct tag file path
            String tagFile = tagService.constructTagFilePath(productType, dateTime);
            log.info("Constructed tag file path for product {} and date {}: {}", productType, dateTime, tagFile);

            if (!tagService.tagFileExists(productType, tagFile)) {
                log.warn("Tag file {} does not exist for product {} and date {}", tagFile, productType, dateTime);
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ErrorResponse("No data found for product '" + productType + "' and date '" + dateTime + "'. Expected tag file: " + tagFile));
            }

            Object response = tagService.getTagsByTagFile(productType, tagFile);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error retrieving tags for product {} and date {}: {}", productType, dateTime, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Error retrieving tags: " + e.getMessage()));
        }
    }

    @GetMapping("/{productType}/tag-files")
    @Operation(summary = "Get all tag files for a product type",
            description = "Retrieve all available tag files for a specific product type")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved tag files"),
            @ApiResponse(responseCode = "400", description = "Invalid product type"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<?> getTagFiles(
            @Parameter(description = "Product type (e.g., surface-waves)", required = true)
            @PathVariable String productType) {

        try {
            log.info("Retrieving tag files for product type: {}", productType);

            // Validate product type
            if (!tagService.getSupportedProductTypes().contains(productType)) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new ErrorResponse("Unsupported product type: " + productType));
            }

            // Ensure data is available
            if (!tagService.isDataAvailable(productType) || !tagService.hasData(productType)) {
                // Try to download data
                boolean downloadSuccess = tagService.downloadData(productType);
                if (!downloadSuccess || !tagService.isDataAvailable(productType)) {
                    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                            .body(new ErrorResponse("Data for product " + productType + " is temporarily unavailable. Please try again in a moment."));
                }
            }

            List<String> tagFiles = tagService.getAllTagFiles(productType);
            return ResponseEntity.ok(Map.of(
                    "productType", productType,
                    "tagFiles", tagFiles,
                    "count", tagFiles.size()
            ));

        } catch (Exception e) {
            log.error("Error retrieving tag files for product {}: {}", productType, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Error retrieving tag files: " + e.getMessage()));
        }
    }

    @PostMapping("/{productType}/download")
    @Operation(summary = "Trigger data download for a product type",
            description = "Manually trigger the download of data for a specific product type")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Download completed successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid product type"),
            @ApiResponse(responseCode = "500", description = "Download failed")
    })
    public ResponseEntity<?> triggerDownload(
            @Parameter(description = "Product type (e.g., surface-waves)", required = true)
            @PathVariable String productType) {

        try {
            log.info("Manual download trigger requested for product type: {}", productType);

            // Validate product type
            if (!tagService.getSupportedProductTypes().contains(productType)) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new ErrorResponse("Unsupported product type: " + productType));
            }

            boolean success = tagService.downloadData(productType);

            if (success) {
                int tagFileCount = 0;
                try {
                    tagFileCount = tagService.getAllTagFiles(productType).size();
                } catch (Exception e) {
                    log.debug("Error getting tag file count after download for product {}: {}", productType, e.getMessage());
                }

                return ResponseEntity.ok(Map.of(
                        "message", "Data downloaded successfully for product " + productType,
                        "productType", productType,
                        "status", "success",
                        "tagFileCount", tagFileCount
                ));
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(new ErrorResponse("Failed to download data for product " + productType + ". Please try again later."));
            }

        } catch (Exception e) {
            log.error("Error during manual download for product {}: {}", productType, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Error downloading data for product " + productType + ": " + e.getMessage()));
        }
    }

    /**
     * Ensure data is available, auto-download if needed
     *
     * @return true if data is available, false if still unavailable after download attempt
     */
    private boolean ensureDataAvailable() {
        try {
            // Quick check if data is already available
            if (tagService.isDataAvailable("surface-waves") && !tagService.getAllTagFiles("surface-waves").isEmpty()) {
                return true;
            }

            // Check if database file exists but has no data (cache issue)
            if (tagService.isDataAvailable("surface-waves") && tagService.getAllTagFiles("surface-waves").isEmpty()) {
                log.info("Database file exists but shows no data - possible cache issue, attempting download...");
            } else {
                log.info("Database not available, attempting automatic download...");
            }

            boolean downloadSuccess = tagService.downloadData("surface-waves");

            if (downloadSuccess) {
                log.info("Automatic download completed successfully");

                // Give a moment for the cache refresh to take effect
                Thread.sleep(2000);

                // Double-check that data is now available
                boolean dataAvailable = tagService.isDataAvailable("surface-waves") && !tagService.getAllTagFiles("surface-waves").isEmpty();
                if (!dataAvailable) {
                    log.warn("Download succeeded but data still not available - may need service restart");
                }
                return dataAvailable;
            } else {
                log.warn("Automatic download failed");
                return false;
            }
        } catch (Exception e) {
            log.error("Error ensuring data availability: {}", e.getMessage(), e);
            return false;
        }
    }


}
