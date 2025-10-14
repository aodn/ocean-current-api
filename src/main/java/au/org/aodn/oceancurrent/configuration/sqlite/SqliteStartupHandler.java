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
     * If the database is not available or doesn't contain required data, it will be downloaded immediately
     */
    private void initializeSqliteDatabase() {
        try {
            log.info("Initializing SQLite database at startup - will download if needed");

            // Since both services share the same database file, we only need to download once
            // First check if either service already has the data available
            boolean buoyDataAvailable = buoyTimeSeriesService.isDataAvailable();
            boolean surfaceWavesDataAvailable = tagService.isDataAvailable("surface-waves");

            if (buoyDataAvailable && surfaceWavesDataAvailable) {
                log.info("SQLite database is already available for all services");
                return;
            }

            // If data is not available for any service, download using the buoy service
            log.info("SQLite database needs to be downloaded - downloading now");
            boolean success = buoyTimeSeriesService.ensureDataAvailability();

            if (success) {
                log.info("SQLite database initialization completed successfully - database is ready for use");

                // Double-check that both services can access the data
                buoyDataAvailable = buoyTimeSeriesService.isDataAvailable();
                surfaceWavesDataAvailable = tagService.isDataAvailable("surface-waves");

                if (!buoyDataAvailable || !surfaceWavesDataAvailable) {
                    log.warn("SQLite database was downloaded but not all services can access it properly");
                }
            } else {
                log.error("[FATAL] SQLite database initialization failed despite download attempt");
                log.warn("The application will attempt to download the database again when needed");
            }

        } catch (Exception e) {
            log.error("[FATAL] Error initializing SQLite database: {}", e.getMessage());
            log.warn("The application will attempt to download the database again when needed");
        }
    }
}
