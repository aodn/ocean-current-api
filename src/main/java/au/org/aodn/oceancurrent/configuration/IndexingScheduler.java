package au.org.aodn.oceancurrent.configuration;

import au.org.aodn.oceancurrent.service.IndexingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@Slf4j
@RequiredArgsConstructor
public class IndexingScheduler {
    private final IndexingService indexingService;

    @Scheduled(cron = "${elasticsearch.indexing.cron.expression:0 0 2 * * ?}")
    public void scheduledIndexing() {
        log.info("Starting scheduled daily indexing");
        try {
            indexingService.indexRemoteJsonFiles(true);
            log.info("Completed scheduled daily indexing");
        } catch (IOException e) {
            log.error("Error during scheduled indexing", e);
        }
    }
}
