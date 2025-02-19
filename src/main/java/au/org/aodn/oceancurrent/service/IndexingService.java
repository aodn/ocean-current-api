package au.org.aodn.oceancurrent.service;

import au.org.aodn.oceancurrent.configuration.AppConstants;
import au.org.aodn.oceancurrent.exception.RemoteFileException;
import au.org.aodn.oceancurrent.model.FileMetadata;
import au.org.aodn.oceancurrent.model.ImageMetadataEntry;
import au.org.aodn.oceancurrent.model.ImageMetadataGroup;
import au.org.aodn.oceancurrent.util.elasticsearch.BulkRequestProcessor;
import au.org.aodn.oceancurrent.util.elasticsearch.IndexingCallback;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.DeleteByQueryRequest;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
@Slf4j
@RequiredArgsConstructor
public class IndexingService {
    private final String indexName = AppConstants.INDEX_NAME;
    private static final int BATCH_SIZE = 100000;
    private static final int THREAD_POOL_SIZE = 2;

    private final ElasticsearchClient esClient;
    private final RemoteJsonService remoteJsonService;

    public void createIndexIfNotExists() throws IOException {
        boolean exists = isIndexExists();

        if (!exists) {
            CreateIndexRequest request = CreateIndexRequest.of(
                    r -> r
                            .index(indexName)
                            .mappings(m -> m
                                    .properties("path", p -> p.keyword(k -> k))
                                    .properties("product", p -> p.keyword(k -> k))
                                    .properties("subProduct", p -> p.keyword(k -> k))
                                    .properties("region", p -> p.keyword(k -> k))
                                    .properties("fileName", p -> p.keyword(k -> k))
                                    .properties("filePath", p -> p.keyword(k -> k))
                            )
            );
            esClient.indices().create(request);
            log.info("Index with name '{}' created", indexName);
        }
    }

    public void deleteIndexIfExists() throws IOException {
        boolean exists = isIndexExists();
        log.info("Delete index with name '{}'", exists);

        if (exists) {
            esClient.indices().delete(c -> c.index(indexName));
            log.info("Index with name '{}' deleted", indexName);
        }
    }

    public void indexRemoteJsonFiles(boolean confirm, IndexingCallback callback) throws IOException {
        if (!confirm) {
            throw new IllegalArgumentException("Please confirm that you want to index all remote JSON files");
        }

        createIndexIfNotExists();

        ExecutorService executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        BulkRequestProcessor bulkRequestProcessor = new BulkRequestProcessor(BATCH_SIZE, indexName, esClient);

        try {
            // Delete existing index
            deleteExistingDocuments();

            // Fetch and process URLs in parallel
            List<String> urls = remoteJsonService.getFullUrls();
            CountDownLatch urlProcessingLatch = new CountDownLatch(urls.size());

            for (String url : urls) {
                submitUrlProcessingTask(executor, url, urlProcessingLatch, bulkRequestProcessor, callback);
            }

            // Submit progress monitoring task
            submitProgressMonitorTask(executor, urlProcessingLatch, callback);

            // Wait for all URL processing to complete
            urlProcessingLatch.await();

            // Flush any remaining documents
            Optional<BulkResponse> finalResponse = bulkRequestProcessor.flush();
            finalResponse.ifPresent(response -> {
                if (callback != null) {
                    callback.onComplete("Indexing completed successfully");
                }
            });

            log.info("Indexing completed successfully for index: {}", indexName);

        } catch (Exception e) {
            log.error("Failed to complete indexing", e);
            if (callback != null) {
                callback.onError("Failed to complete indexing: " + e.getMessage());
            }
            throw new RuntimeException("Indexing failed", e);
        } finally {
            executor.shutdown();
        }
    }

    private void submitUrlProcessingTask(
            ExecutorService executor,
            String url,
            CountDownLatch urlProcessingLatch,
            BulkRequestProcessor bulkRequestProcessor,
            IndexingCallback callback) {

        executor.submit(() -> {
            try {
                processUrl(url, bulkRequestProcessor, callback);
            } catch (Exception e) {
                log.error("Error processing URL: {}", url, e);
                if (callback != null) {
                    callback.onProgress("Failed to process URL: " + url);
                }
            } finally {
                urlProcessingLatch.countDown();
            }
        });
    }

    private void processUrl(String url, BulkRequestProcessor bulkRequestProcessor, IndexingCallback callback) {
        log.info("Processing URL: {}", url);
        try {
            List<ImageMetadataGroup> metadata = remoteJsonService.fetchJsonFromUrl(url);
            for (ImageMetadataGroup group : metadata) {
                processMetadataGroup(group, bulkRequestProcessor);
                if (callback != null) {
                    callback.onProgress("Processed metadata group: " + group.getProduct());
                }
            }
        } catch (RemoteFileException e) {
            log.error("Failed to process URL: {}", url, e);
        }
    }

    private void processMetadataGroup(ImageMetadataGroup group, BulkRequestProcessor bulkRequestProcessor) {
        group.getFiles().forEach(file -> {
            ImageMetadataEntry doc = createMetadataEntry(group, file);
            bulkRequestProcessor.addDocument(doc);
        });
    }

    private ImageMetadataEntry createMetadataEntry(ImageMetadataGroup group, FileMetadata file) {
        ImageMetadataEntry doc = new ImageMetadataEntry();
        doc.setProduct(group.getProduct());
        doc.setSubProduct(group.getSubProduct());
        doc.setRegion(group.getRegion());
        doc.setPath(group.getPath());
        doc.setFileName(file.getName());
        doc.setFilePath(file.getPath());
        return doc;
    }

    private void submitProgressMonitorTask(
            ExecutorService executor,
            CountDownLatch urlProcessingLatch,
            IndexingCallback callback) {

        if (callback != null) {
            executor.submit(() -> {
                try {
                    while (urlProcessingLatch.getCount() > 0) {
                        callback.onProgress("Processing... " +
                                (urlProcessingLatch.getCount() + " URLs remaining"));
                        Thread.sleep(5000);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }
    }

    private void deleteExistingDocuments() throws IOException {
        DeleteByQueryRequest deleteRequest = DeleteByQueryRequest.of(d -> d
                .index(indexName)
                .query(q -> q.matchAll(m -> m))
        );
        log.info("Deleting existing documents for index: {}", indexName);
        esClient.deleteByQuery(deleteRequest);
        log.info("Existing documents deleted");
    }

    private boolean isIndexExists() throws IOException {
        return esClient.indices()
                .exists(c -> c.index(indexName))
                .value();
    }
}
