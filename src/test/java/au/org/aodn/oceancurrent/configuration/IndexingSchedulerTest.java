package au.org.aodn.oceancurrent.configuration;

import au.org.aodn.oceancurrent.service.IndexingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IndexingSchedulerTest {

    @Mock
    private IndexingService indexingService;

    @InjectMocks
    private IndexingScheduler indexingScheduler;

    @BeforeEach
    void setUp() {
    }

    @Test
    void testScheduledIndexingSuccessful() throws IOException {
        // Arrange
        doNothing().when(indexingService).indexRemoteJsonFiles(true);

        // Act
        indexingScheduler.scheduledIndexing();

        // Assert
        verify(indexingService, times(1)).indexRemoteJsonFiles(true);
    }

    @Test
    void testScheduledIndexingHandlesException() throws IOException {
        // Arrange
        doThrow(IOException.class).when(indexingService).indexRemoteJsonFiles(true);

        // Act
        indexingScheduler.scheduledIndexing();

        // Assert
        verify(indexingService, times(1)).indexRemoteJsonFiles(true);
        // Note: We're only verifying the method was called
        // The exception is caught in the method, so we don't need to assert anything about it
    }
}
