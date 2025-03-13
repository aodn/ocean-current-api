package au.org.aodn.oceancurrent.configuration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.test.context.TestPropertySource;

import java.lang.reflect.Method;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@TestPropertySource(properties = {
        "elasticsearch.indexing.cron.expression=0 0 2 * * ?",
})
public class IndexingSchedulerIntegrationTest {

    @Value("${elasticsearch.indexing.cron.expression}")
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

        // Verify next execution time is as expected (should be 2 AM tomorrow)
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime nextRun = expression.next(now);
        assertNotNull(nextRun, "Next run time should be calculated");
        assertEquals(2, nextRun.getHour(), "Scheduled hour should be 2 AM");

        // Check that the scheduled method exists
        try {
            Method scheduledMethod = IndexingScheduler.class.getMethod("scheduledIndexing");
            assertNotNull(scheduledMethod, "Scheduled method should exist");
        } catch (NoSuchMethodException e) {
            throw new AssertionError("Scheduled method not found", e);
        }
    }

    @Test
    void testCronExpressionValue() {
        assertEquals("0 0 2 * * ?", configuredCronExpression,
                "Configured cron expression should match expected value");
    }
}
