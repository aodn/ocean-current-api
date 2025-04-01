package au.org.aodn.oceancurrent.configuration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.test.context.TestPropertySource;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@TestPropertySource(properties = {
        "elasticsearch.indexing.cron.expression=0 0 2 * * ?",
})
public class IndexingSchedulerIntegrationTest {

    @Value("${elasticsearch.indexing.cron.expression:0 0 2 * * ?}")
    private String configuredCronExpression;

    @Autowired
    private IndexingScheduler indexingScheduler;

    @Test
    void testSchedulerIsConfigured() {
        // Verify that the scheduler is created
        assertNotNull(indexingScheduler, "Scheduled indexing scheduler is null");

        // Verify the cron expression is valid
        CronExpression expression = CronExpression.parse(configuredCronExpression);
        assertNotNull(expression, "Cron expression should be valid");

        // When scheduled for 2 AM Hobart time, the UTC time would be either 15:00 (3 PM)
        // or 16:00 (4 PM) of the previous day, depending on daylight saving
        LocalDateTime now = LocalDateTime.now(ZoneId.of("UTC"));
        LocalDateTime nextRun = expression.next(now);
        assertNotNull(nextRun, "Next run time should be calculated");
        assertEquals("0 0 2 * * ?", configuredCronExpression,
                "Cron expression should be correctly formatted for 2 AM execution");

        try {
            Method scheduledMethod = IndexingScheduler.class.getMethod("scheduledIndexing");
            assertNotNull(scheduledMethod, "Scheduled method should exist");
            Scheduled annotation = scheduledMethod.getAnnotation(Scheduled.class);
            assertNotNull(annotation, "Method should have @Scheduled annotation");
            assertTrue(annotation.cron().contains("${elasticsearch.indexing.cron.expression"),
                    "Annotation should reference the property");
            assertEquals(
                    "Australia/Hobart",
                    annotation.zone(),
                    "Timezone should be set to Australia/Hobart for consistent execution");
        } catch (NoSuchMethodException e) {
            throw new AssertionError("Scheduled method not found", e);
        }
    }
}
