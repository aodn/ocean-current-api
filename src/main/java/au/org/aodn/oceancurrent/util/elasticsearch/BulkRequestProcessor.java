package au.org.aodn.oceancurrent.util.elasticsearch;

import au.org.aodn.oceancurrent.model.ImageMetadataEntry;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.client.ResponseException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Getter
@Setter
public class BulkRequestProcessor {
    private static final int MAX_RETRIES = 3;

    private final int batchSize;
    private final List<ImageMetadataEntry> currentBatch;
    private final ElasticsearchClient esClient;
    private final String indexName;
    private final long retryBaseDelayMs;

    public BulkRequestProcessor(int batchSize, String indexName, ElasticsearchClient esClient) {
        this(batchSize, indexName, esClient, 5000);
    }

    // Package-private constructor for testing — allows overriding retry delay
    BulkRequestProcessor(int batchSize, String indexName, ElasticsearchClient esClient, long retryBaseDelayMs) {
        this.batchSize = batchSize;
        this.esClient = esClient;
        this.indexName = indexName;
        this.currentBatch = new ArrayList<>(batchSize);
        this.retryBaseDelayMs = retryBaseDelayMs;
    }

    public synchronized void addDocument(ImageMetadataEntry doc) {
        currentBatch.add(doc);
        if (currentBatch.size() >= batchSize) {
            flush();
        }
    }

    public synchronized void flush() {
        if (currentBatch.isEmpty()) {
            return;
        }

        BulkRequest.Builder bulkRequestBuilder = new BulkRequest.Builder();

        for (ImageMetadataEntry doc : currentBatch) {
            String documentId = DocumentIdGenerator.generateDocumentId(doc);
            bulkRequestBuilder.operations(op -> op
                    .index(idx -> idx
                            .index(indexName)
                            .id(documentId)
                            .document(doc)
                    )
            );
        }

        BulkRequest request = bulkRequestBuilder.build();

        try {
            executeWithRetry(request);
        } finally {
            currentBatch.clear();
        }
    }

    private void executeWithRetry(BulkRequest request) {
        int attempt = 0;
        while (true) {
            try {
                log.info("Bulk indexing {} documents", request.operations().size());
                BulkResponse response = esClient.bulk(request);
                log.info("Bulk indexing completed");
                if (response.errors()) {
                    log.error("Bulk indexing has errors!");
                    response.items().forEach(item -> {
                        if (item.error() != null) {
                            log.error("Error for item: {}", item.error().reason());
                        }
                    });
                }
                return;
            } catch (IOException e) {
                if (is429(e) && attempt < MAX_RETRIES) {
                    attempt++;
                    long delay = retryBaseDelayMs * attempt;
                    log.warn("Bulk indexing rejected by circuit breaker (429), retry {}/{} after {}ms", attempt, MAX_RETRIES, delay);
                    try {
                        //noinspection BusyWait
                        Thread.sleep(delay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Bulk indexing interrupted during retry", ie);
                    }
                } else {
                    log.error("Error during bulk indexing", e);
                    throw new RuntimeException("Bulk indexing failed", e);
                }
            }
        }
    }

    private boolean is429(IOException e) {
        return e instanceof ResponseException re && re.getResponse().getStatusLine().getStatusCode() == 429;
    }
}
