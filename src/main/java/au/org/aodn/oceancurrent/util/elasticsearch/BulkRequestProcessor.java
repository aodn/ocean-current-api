package au.org.aodn.oceancurrent.util.elasticsearch;

import au.org.aodn.oceancurrent.model.ImageMetadataEntry;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import co.elastic.clients.transport.TransportException;
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
                    log.warn("Bulk indexing received 429 Too Many Requests, retry {}/{} after {}ms", attempt, MAX_RETRIES, delay);
                    try {
                        //noinspection BusyWait
                        Thread.sleep(delay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Bulk indexing interrupted during retry", ie);
                    }
                } else if (is413(e)) {
                    List<BulkOperation> ops = request.operations();
                    if (ops.size() == 1) {
                        log.error("Single document is too large for bulk indexing (413), skipping");
                        return;
                    }
                    int mid = ops.size() / 2;
                    log.warn("Bulk request too large (413), splitting {} documents into two halves and retrying", ops.size());
                    executeWithRetry(new BulkRequest.Builder().operations(ops.subList(0, mid)).build());
                    executeWithRetry(new BulkRequest.Builder().operations(ops.subList(mid, ops.size())).build());
                    return;
                } else {
                    log.error("Error during bulk indexing", e);
                    throw new RuntimeException("Bulk indexing failed", e);
                }
            }
        }
    }

    private boolean is429(IOException e) {
        return getHttpStatusCode(e) == 429;
    }

    private boolean is413(IOException e) {
        return getHttpStatusCode(e) == 413;
    }

    private int getHttpStatusCode(IOException e) {
        if (e instanceof TransportException te) {
            return te.statusCode();
        }
        if (e instanceof ResponseException re) {
            return re.getResponse().getStatusLine().getStatusCode();
        }
        return -1;
    }
}
