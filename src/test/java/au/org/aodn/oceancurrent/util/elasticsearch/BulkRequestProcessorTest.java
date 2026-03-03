package au.org.aodn.oceancurrent.util.elasticsearch;

import au.org.aodn.oceancurrent.model.ImageMetadataEntry;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.transport.TransportException;
import co.elastic.clients.transport.http.TransportHttpClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BulkRequestProcessorTest {

    @Mock
    private ElasticsearchClient esClient;

    @Mock
    private BulkResponse bulkResponse;

    @Test
    void flush_retriesOn429AndSucceeds() throws Exception {
        // Given
        doThrow(createException(429))
                .doReturn(bulkResponse)
                .when(esClient).bulk(any(BulkRequest.class));
        when(bulkResponse.errors()).thenReturn(false);

        BulkRequestProcessor processor = new BulkRequestProcessor(1, "test-index", esClient, 0);

        // When - batchSize=1 so adding one document immediately triggers a flush
        processor.addDocument(new ImageMetadataEntry());

        // Then - bulk was called twice: 1 failed attempt + 1 successful retry
        verify(esClient, times(2)).bulk(any(BulkRequest.class));
    }

    @Test
    void flush_failsAfterMaxRetries() throws Exception {
        // Given - always returns 429
        doThrow(createException(429)).when(esClient).bulk(any(BulkRequest.class));

        BulkRequestProcessor processor = new BulkRequestProcessor(1, "test-index", esClient, 0);

        // When / Then
        assertThatThrownBy(() -> processor.addDocument(new ImageMetadataEntry()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Bulk indexing failed");

        // 1 initial attempt + 3 retries = 4 total calls
        verify(esClient, times(4)).bulk(any(BulkRequest.class));
    }

    @Test
    void flush_doesNotRetryOnNon429Error() throws Exception {
        // Given - a non-retryable IOException (e.g. mapping conflict, auth failure)
        doThrow(new java.io.IOException("Mapping error")).when(esClient).bulk(any(BulkRequest.class));

        BulkRequestProcessor processor = new BulkRequestProcessor(1, "test-index", esClient, 0);

        // When / Then
        assertThatThrownBy(() -> processor.addDocument(new ImageMetadataEntry()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Bulk indexing failed");

        // No retry — only 1 call
        verify(esClient, times(1)).bulk(any(BulkRequest.class));
    }

    @Test
    void flush_splitsOnce_on413() throws Exception {
        // Given - first call (full batch) throws 413; the two split halves succeed
        doThrow(createException(413))
                .doReturn(bulkResponse)
                .doReturn(bulkResponse)
                .when(esClient).bulk(any(BulkRequest.class));
        when(bulkResponse.errors()).thenReturn(false);

        BulkRequestProcessor processor = new BulkRequestProcessor(2, "test-index", esClient, 0);

        // When - batchSize=2 so adding two documents triggers a single flush
        processor.addDocument(new ImageMetadataEntry());
        processor.addDocument(new ImageMetadataEntry());

        // Then - 1 failed attempt + 2 successful retries (split into 1+1)
        verify(esClient, times(3)).bulk(any(BulkRequest.class));
    }

    @Test
    void flush_skipsDocumentOn413_whenSingleDoc() throws Exception {
        // Given - single document is too large
        doThrow(createException(413)).when(esClient).bulk(any(BulkRequest.class));

        BulkRequestProcessor processor = new BulkRequestProcessor(1, "test-index", esClient, 0);

        // When / Then - oversized single document is skipped without throwing
        processor.addDocument(new ImageMetadataEntry());

        // Only one attempt — cannot split further
        verify(esClient, times(1)).bulk(any(BulkRequest.class));
    }

    /**
     * Creates a TransportException with the given HTTP status code, matching the exception type
     * that co.elastic.clients.transport surfaces at runtime for HTTP-level errors.
     */
    private TransportException createException(int statusCode) {
        TransportHttpClient.Response httpResponse = mock(TransportHttpClient.Response.class);
        when(httpResponse.statusCode()).thenReturn(statusCode);
        return new TransportException(httpResponse, "HTTP " + statusCode, "bulk");
    }
}
