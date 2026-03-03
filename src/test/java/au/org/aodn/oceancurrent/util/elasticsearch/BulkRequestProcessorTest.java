package au.org.aodn.oceancurrent.util.elasticsearch;

import au.org.aodn.oceancurrent.model.ImageMetadataEntry;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import org.apache.http.HttpVersion;
import org.apache.http.message.BasicRequestLine;
import org.apache.http.message.BasicStatusLine;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.ResponseException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;

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
        doThrow(create429Exception())
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
        doThrow(create429Exception()).when(esClient).bulk(any(BulkRequest.class));

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
        // Given - a non-429 IOException (e.g. mapping conflict, auth failure)
        doThrow(new IOException("Mapping error")).when(esClient).bulk(any(BulkRequest.class));

        BulkRequestProcessor processor = new BulkRequestProcessor(1, "test-index", esClient, 0);

        // When / Then
        assertThatThrownBy(() -> processor.addDocument(new ImageMetadataEntry()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Bulk indexing failed");

        // No retry — only 1 call
        verify(esClient, times(1)).bulk(any(BulkRequest.class));
    }

    private ResponseException create429Exception() throws IOException {
        Response response = mock(Response.class);
        when(response.getRequestLine()).thenReturn(new BasicRequestLine("POST", "/_bulk", HttpVersion.HTTP_1_1));
        when(response.getHost()).thenReturn(new org.apache.http.HttpHost("localhost", 9200, "https"));
        when(response.getStatusLine()).thenReturn(new BasicStatusLine(HttpVersion.HTTP_1_1, 429, "Too Many Requests"));
        when(response.getEntity()).thenReturn(null);
        return new ResponseException(response);
    }
}
