package au.org.aodn.oceancurrent.configuration;

import au.org.aodn.oceancurrent.service.IndexingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Component
@Slf4j
@RequiredArgsConstructor
public class IndexingScheduler {
    private final IndexingService indexingService;

    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.of("UTC"));

    @Scheduled(cron = "${elasticsearch.indexing.cron.expression:0 0 2 * * ?}", zone = "Australia/Hobart")
    public void scheduledIndexing() {
        String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
        log.info("Starting scheduled daily indexing at {} UTC (2 AM Australia/Hobart time)", timestamp);

        try {
            Instant startTime = Instant.now();
            indexingService.indexRemoteJsonFiles(true);
            Duration duration = Duration.between(startTime, Instant.now());

            log.info("Completed scheduled daily indexing in {} minutes and {} seconds",
                    duration.toMinutes(),
                    duration.minusMinutes(duration.toMinutes()).getSeconds());
        } catch (IOException e) {
            log.error("Error during scheduled indexing", e);
        }
    }

    @Scheduled(cron = "${elasticsearch.indexing.s3.cron.expression:0 30 2 * * ?}", zone = "Australia/Hobart")
    public void scheduledS3Indexing() {
        String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
        log.info("Starting scheduled daily S3 indexing at {} UTC (2:30 AM Australia/Hobart time)", timestamp);

        try {
            Instant startTime = Instant.now();
            indexingService.indexS3SurfaceWavesFiles(true);
            Duration duration = Duration.between(startTime, Instant.now());

            log.info("Completed scheduled daily S3 indexing in {} minutes and {} seconds",
                    duration.toMinutes(),
                    duration.minusMinutes(duration.toMinutes()).getSeconds());
        } catch (IOException e) {
            log.error("Error during scheduled S3 indexing", e);
        }
    }
}
