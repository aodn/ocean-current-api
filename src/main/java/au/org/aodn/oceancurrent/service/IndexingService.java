package au.org.aodn.oceancurrent.service;

import au.org.aodn.oceancurrent.configuration.AppConstants;
import au.org.aodn.oceancurrent.configuration.elasticsearch.ElasticsearchProperties;
import au.org.aodn.oceancurrent.constant.CacheNames;
import au.org.aodn.oceancurrent.exception.RemoteFileException;
import au.org.aodn.oceancurrent.model.FileMetadata;
import au.org.aodn.oceancurrent.model.ImageMetadataEntry;
import au.org.aodn.oceancurrent.model.RemoteJsonDataGroup;
import au.org.aodn.oceancurrent.util.elasticsearch.BulkRequestProcessor;
import au.org.aodn.oceancurrent.util.elasticsearch.IndexingCallback;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static au.org.aodn.oceancurrent.constant.ProductConstants.PRODUCT_ID_MAPPINGS;

@Service
@Slf4j
@RequiredArgsConstructor
public class IndexingService {
    private final String indexName = AppConstants.INDEX_NAME;
    private static final int BATCH_SIZE = 100000;
    private static final int THREAD_POOL_SIZE = 2;

    private final ElasticsearchClient esClient;
    private final RemoteJsonService remoteJsonService;
    private final ElasticsearchProperties esProperties;
    private final CacheManager cacheManager;

    public void createIndexIfNotExists() throws IOException {
        boolean exists = isIndexExists();

        if (!exists) {
            CreateIndexRequest request = CreateIndexRequest.of(
                    r -> r
                            .index(indexName)
                            .settings(s -> s
                                    .index(i -> i
                                            .maxResultWindow(esProperties.getMaxResultWindow())
                                    )
                            )
                            .mappings(m -> m
                                    .properties("path", p -> p.keyword(k -> k))
                                    .properties("productId", p -> p.keyword(k -> k))
                                    .properties("region", p -> p.keyword(k -> k))
                                    .properties("fileName", p -> p.keyword(k -> k))
                                    .properties("depth", p -> p.keyword(k -> k))
                            )
            );
            esClient.indices().create(request);
            log.info("Index with name '{}' created with max_result_window of {}",
                    indexName, esProperties.getMaxResultWindow());
        }
    }

    public void deleteIndexIfExists() throws IOException {
        boolean exists = isIndexExists();

        if (exists) {
            log.info("Deleting index with name '{}'", indexName);
            esClient.indices().delete(c -> c.index(indexName));
            log.info("Index with name '{}' deleted", indexName);
        } else {
            log.warn("Index with name '{}' does not exist", indexName);
        }
    }

    public void indexRemoteJsonFiles(boolean confirm) throws IOException {
        indexRemoteJsonFiles(confirm, null);
    }

    @CacheEvict(value = CacheNames.IMAGE_LIST, allEntries = true)
    public void indexRemoteJsonFiles(boolean confirm, IndexingCallback callback) throws IOException {
        if (!confirm) {
            throw new IllegalArgumentException("Please confirm that you want to index all remote JSON files");
        }

        log.info("Starting indexing process");

        ExecutorService executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        BulkRequestProcessor bulkRequestProcessor = new BulkRequestProcessor(BATCH_SIZE, indexName, esClient);

        try {
            deleteIndexIfExists();
            if (callback != null) {
                callback.onProgress("Existing index deleted");
            }

            createIndexIfNotExists();
            if (callback != null) {
                callback.onProgress("Recreated index, starting indexing");
            }

            // Fetch and process URLs in parallel
            List<String> urls = remoteJsonService.getFullUrls();
            log.info("Found {} URLs to process", urls.size());
            if (callback != null) {
                callback.onProgress("Starting to process " + urls.size() + " files");
            }

            CountDownLatch urlProcessingLatch = new CountDownLatch(urls.size());

            for (String url : urls) {
                log.info("Submitting URL for processing: {}", url);
                submitUrlProcessingTask(executor, url, urlProcessingLatch, bulkRequestProcessor, callback);
            }

            // Wait for all URL processing to complete
            urlProcessingLatch.await();

            // Flush any remaining documents
            Optional<BulkResponse> finalResponse = bulkRequestProcessor.flush();
            log.info("Indexing completed successfully for index: {}", indexName);
            finalResponse.ifPresent(response -> {
                if (callback != null) {
                    callback.onComplete("Indexing completed successfully");
                }
            });

            log.info("Clearing cache '{}' after indexing", CacheNames.IMAGE_LIST);

            Cache imageListCache = cacheManager.getCache(CacheNames.IMAGE_LIST);
            if (imageListCache != null) {
                imageListCache.clear();
            } else {
                log.warn("Cache {} not found", CacheNames.IMAGE_LIST);
            }

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
            List<RemoteJsonDataGroup> remoteJsonMetadata = remoteJsonService.fetchJsonFromUrl(url);
            log.info("Successfully processed URL: {}", url);
            if (callback != null) {
                callback.onProgress("Processing file: " + url);
            }

            for (RemoteJsonDataGroup group : remoteJsonMetadata) {
                processRemoteJsonMetadataGroup(group, bulkRequestProcessor);
                if (callback != null) {
                    callback.onProgress(
                            "Processed remote JSON metadata group: "
                                    + group.getProductId()
                    );
                }
            }
        } catch (RemoteFileException e) {
            log.error("Failed to process URL: {}", url, e);
            if (callback != null) {
                callback.onError("Failed to process file: " + url);
            }
        }
    }

    private void processRemoteJsonMetadataGroup(RemoteJsonDataGroup group, BulkRequestProcessor bulkRequestProcessor) {
        group.getFiles().forEach(file -> {
            ImageMetadataEntry doc = createMetadataEntryFromJson(group, file);
            bulkRequestProcessor.addDocument(doc);
        });
    }

    private ImageMetadataEntry createMetadataEntryFromJson(RemoteJsonDataGroup group, FileMetadata file) {
        ImageMetadataEntry doc = new ImageMetadataEntry();

        String productId = PRODUCT_ID_MAPPINGS.getOrDefault(group.getProductId(), group.getProductId());

        doc.setProductId(productId);
        doc.setRegion(group.getRegion());
        doc.setPath(group.getPath());
        doc.setDepth(group.getDepth());
        doc.setFileName(file.getName());
        return doc;
    }

    private boolean isIndexExists() throws IOException {
        return esClient.indices()
                .exists(c -> c.index(indexName))
                .value();
    }
}
