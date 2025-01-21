package au.org.aodn.oceancurrent.util.elasticsearch;

import au.org.aodn.oceancurrent.configuration.AppConstants;
import au.org.aodn.oceancurrent.model.ImageMetadataEntry;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Getter
@Setter
public class BulkRequestProcessor {
    private final int batchSize;
    private final List<ImageMetadataEntry> currentBatch;
    private final ElasticsearchClient esClient;
    private final String indexName;

    public BulkRequestProcessor(int batchSize, String indexName, ElasticsearchClient esClient) {
        this.batchSize = batchSize;
        this.esClient = esClient;
        this.indexName = indexName;
        this.currentBatch = new ArrayList<>(batchSize);
    }

    public synchronized void addDocument(ImageMetadataEntry doc) {
        currentBatch.add(doc);
        if (currentBatch.size() >= batchSize) {
            flush();
        }
    }

    public synchronized Optional<BulkResponse> flush() {
        if (currentBatch.isEmpty()) {
            return Optional.empty();
        }

        BulkRequest.Builder bulkRequest = new BulkRequest.Builder();

        for (ImageMetadataEntry doc : currentBatch) {
            bulkRequest.operations(op -> op
                    .index(idx -> idx
                            .index(indexName)
                            .document(doc)
                    )
            );
        }

        try {
            log.info("Bulk indexing {} documents", currentBatch.size());
            BulkResponse response = esClient.bulk(bulkRequest.build());
            log.info("Bulk indexing completed");
            if (response.errors()) {
                log.error("Bulk indexing has errors!");
                response.items().forEach(item -> {
                    if (item.error() != null) {
                        log.error("Error for item: {}", item.error().reason());
                    }
                });
            }
            return Optional.of(response);
        } catch (IOException e) {
            log.error("Error during bulk indexing", e);
            throw new RuntimeException("Bulk indexing failed", e);
        } finally {
            currentBatch.clear();
        }
    }
}
