package au.org.aodn.oceancurrent.configuration.sqlite;

import au.org.aodn.oceancurrent.service.BuoyTimeSeriesService;
import au.org.aodn.oceancurrent.service.tags.TagService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Handles SQLite database initialization at application startup
 * All services share the same SQLite database file
 */
@Slf4j
@Component
public class SqliteStartupHandler implements ApplicationRunner {

    private final TagService tagService;
    private final BuoyTimeSeriesService buoyTimeSeriesService;

    @Autowired
    public SqliteStartupHandler(TagService tagService, BuoyTimeSeriesService buoyTimeSeriesService) {
        this.tagService = tagService;
        this.buoyTimeSeriesService = buoyTimeSeriesService;
    }

    @Override
    public void run(ApplicationArguments args) {
        initializeSqliteDatabase();
    }

    /**
     * Initialize the shared SQLite database
     * This database contains both surface waves and buoy time series data
     */
    private void initializeSqliteDatabase() {
        try {
            log.info("Initializing SQLite database...");

            // Check if the database is available using any service (they all use the same file)
            if (!buoyTimeSeriesService.isDatabaseAvailable()) {
                log.info("SQLite database not available, will be created on first API call");
                return;
            }

            log.info("SQLite database file is available");

            // Check if surface waves data is available
            boolean hasSurfaceWavesData = false;
            try {
                hasSurfaceWavesData = !tagService.getAllTagFiles("surface-waves").isEmpty();
                log.info("Surface waves data is {}", hasSurfaceWavesData ? "available" : "not available");
            } catch (Exception e) {
                log.debug("Error checking surface waves data: {}", e.getMessage());
            }

            // Download the database if no surface waves data
            if (!hasSurfaceWavesData) {
                log.info("No surface waves data found, automatically downloading database...");
                boolean success = tagService.downloadData("surface-waves");
                if (success) {
                    log.info("SQLite database downloaded successfully on startup");
                } else {
                    log.warn("Failed to download SQLite database on startup - will retry on API calls");
                }
            }

        } catch (Exception e) {
            log.warn("Error initializing SQLite database: {}. Will retry on API calls.", e.getMessage());
        }
    }
}
