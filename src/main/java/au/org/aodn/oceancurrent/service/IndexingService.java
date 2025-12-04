package au.org.aodn.oceancurrent.service;

import au.org.aodn.oceancurrent.configuration.elasticsearch.ElasticsearchProperties;
import au.org.aodn.oceancurrent.constant.CacheNames;
import au.org.aodn.oceancurrent.exception.ReindexValidationException;
import au.org.aodn.oceancurrent.exception.RemoteFileException;
import au.org.aodn.oceancurrent.model.FileMetadata;
import au.org.aodn.oceancurrent.model.ImageMetadataEntry;
import au.org.aodn.oceancurrent.model.RemoteJsonDataGroup;
import au.org.aodn.oceancurrent.util.elasticsearch.BulkRequestProcessor;
import au.org.aodn.oceancurrent.util.elasticsearch.IndexingCallback;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.GetAliasResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static au.org.aodn.oceancurrent.constant.ProductConstants.PRODUCT_ID_MAPPINGS;

@Service
@Slf4j
public class IndexingService {
    private final String indexAlias;
    private static final int BATCH_SIZE = 100000;
    private static final int THREAD_POOL_SIZE = 2;
    private static final DateTimeFormatter INDEX_TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd't'HHmmssSSS");

    private final ElasticsearchClient esClient;
    private final RemoteJsonService remoteJsonService;
    private final S3Service s3Service;
    private final ElasticsearchProperties esProperties;
    private final CacheManager cacheManager;

    public IndexingService(ElasticsearchClient esClient,
                           RemoteJsonService remoteJsonService,
                           S3Service s3Service,
                           ElasticsearchProperties esProperties,
                           CacheManager cacheManager) {
        this.esClient = esClient;
        this.remoteJsonService = remoteJsonService;
        this.s3Service = s3Service;
        this.esProperties = esProperties;
        this.indexAlias = esProperties.getIndexName();
        this.cacheManager = cacheManager;
    }

    public void deleteIndexIfExists() throws IOException {
        boolean exists = isIndexExists();

        if (exists) {
            log.info("Deleting index with name '{}'", indexAlias);
            esClient.indices().delete(c -> c.index(indexAlias));
            log.info("Index with name '{}' deleted", indexAlias);
        } else {
            log.warn("Index with name '{}' does not exist", indexAlias);
        }
    }

   /**
    * @deprecated Use reindexAll() instead for complete reindexing with zero downtime.
    * This method is kept for backward compatibility only.
    */
   @Deprecated
   public void indexS3SurfaceWavesFiles(boolean confirm) throws IOException {
       reindexAll(confirm, null);
   }

   /**
    * @deprecated Use reindexAll() instead for complete reindexing with zero downtime.
    * This method is kept for backward compatibility only.
    */
   @Deprecated
   public void indexS3SurfaceWavesFiles(boolean confirm, IndexingCallback callback) throws IOException {
       log.warn("indexS3SurfaceWavesFiles() is deprecated. Calling reindexAll() instead.");
       reindexAll(confirm, callback);
   }

    /**
     * Indexes both remote JSON files and S3 files into a single new index with zero downtime.
     * This is the recommended method for full reindexing.
     */
    @CacheEvict(value = CacheNames.IMAGE_LIST, allEntries = true)
    public void reindexAll(boolean confirm, IndexingCallback callback) throws IOException {
        if (!confirm) {
            throw new IllegalArgumentException("Please confirm that you want to reindex all files");
        }

        log.info("Starting full reindexing process");

        // Generate a new timestamped index name
        String newIndexName = generateTimestampedIndexName(indexAlias);
        log.info("Creating new index: {}", newIndexName);

        // Get old indices before creating new one
        Set<String> oldIndices = getIndicesForAlias(indexAlias);

        ExecutorService executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);

        try {
            // Create the new timestamped index
            createIndex(newIndexName);
            if (callback != null) {
                callback.onProgress("Created new index: " + newIndexName);
            }

            // Index remote JSON files
            indexRemoteJsonFilesIntoIndex(newIndexName, executor, callback);

            // Index S3 files
            indexS3FilesIntoIndex(newIndexName, callback);

            // Refresh the new index to ensure all documents are searchable
            refreshIndex(newIndexName);
            if (callback != null) {
                callback.onProgress("Refreshed new index to ensure all documents are searchable");
            }

            // Validate the new index before switching alias
            validateNewIndex(newIndexName, indexAlias, callback);

            // Atomically switch the alias from old index to new index
            switchAlias(indexAlias, newIndexName);
            if (callback != null) {
                callback.onProgress("Switched alias '" + indexAlias + "' to new index");
            }

            // Delete old indices
            deleteOldIndices(oldIndices);
            if (callback != null) {
                callback.onProgress("Cleaned up old indices");
            }

            if (callback != null) {
                callback.onComplete("Full reindexing completed successfully");
            }

            clearImageListCache();

        } catch (Exception e) {
            log.error("Failed to complete full reindexing", e);
            if (callback != null) {
                callback.onError("Failed to complete full reindexing: " + e.getMessage());
            }
            // Clean up the failed new index
            try {
                if (isIndexExists(newIndexName)) {
                    esClient.indices().delete(d -> d.index(newIndexName));
                    log.info("Cleaned up failed index: {}", newIndexName);
                }
            } catch (Exception cleanupError) {
                log.error("Failed to clean up index '{}': {}", newIndexName, cleanupError.getMessage());
            }
            throw new RuntimeException("Full reindexing failed", e);
        } finally {
            executor.shutdown();
        }
    }

    public void reindexAll(boolean confirm) throws IOException {
        reindexAll(confirm, null);
    }

    /**
     * Indexes remote JSON files into the specified index.
     * This is a helper method used by both indexRemoteJsonFiles and reindexAll.
     */
    private void indexRemoteJsonFilesIntoIndex(String targetIndexName, ExecutorService executor, IndexingCallback callback) throws Exception {
        BulkRequestProcessor bulkRequestProcessor = new BulkRequestProcessor(BATCH_SIZE, targetIndexName, esClient);

        // Fetch and process URLs in parallel
        List<String> urls = remoteJsonService.getFullUrls();
        log.info("Found {} URLs to process", urls.size());
        if (callback != null) {
            callback.onProgress("Starting to process " + urls.size() + " remote JSON files");
        }

        CountDownLatch urlProcessingLatch = new CountDownLatch(urls.size());

        for (String url : urls) {
            log.info("Submitting URL for processing: {}", url);
            submitUrlProcessingTask(executor, url, urlProcessingLatch, bulkRequestProcessor, callback);
        }

        // Wait for all URL processing to complete
        urlProcessingLatch.await();

        // Flush any remaining documents
        bulkRequestProcessor.flush();
        log.info("Remote JSON indexing completed successfully for index: {}", targetIndexName);
    }

    /**
     * Indexes S3 surface waves files into the specified index.
     * This is a helper method used by reindexAll.
     */
    private void indexS3FilesIntoIndex(String targetIndexName, IndexingCallback callback) {
        BulkRequestProcessor bulkRequestProcessor = new BulkRequestProcessor(BATCH_SIZE, targetIndexName, esClient);

        // Validate S3 access before starting
        if (!s3Service.isBucketAccessible()) {
            String errorMsg = "S3 bucket is not accessible. Please check bucket configuration and permissions.";
            log.error(errorMsg);
            if (callback != null) {
                callback.onError(errorMsg);
            }
            throw new RuntimeException(errorMsg);
        }

        log.info("Listing all S3 objects for indexing");

        List<ImageMetadataEntry> s3Entries = s3Service.listAllSurfaceWaves();
        if (s3Entries.isEmpty()) {
            log.warn("No S3 objects found to index");
            if (callback != null) {
                callback.onProgress("No S3 files found to index");
            }
            return;
        }

        log.info("Found {} S3 objects to index", s3Entries.size());

        if (callback != null) {
            callback.onProgress("Starting to process " + s3Entries.size() + " S3 files");
        }

        for (ImageMetadataEntry entry : s3Entries) {
            bulkRequestProcessor.addDocument(entry);
        }

        // Flush any remaining documents
        bulkRequestProcessor.flush();
        log.info("S3 indexing completed successfully for index: {}", targetIndexName);
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
        return isIndexExists(indexAlias);
    }

    private boolean isIndexExists(String index) throws IOException {
        return esClient.indices()
                .exists(c -> c.index(index))
                .value();
    }

    private void clearImageListCache() {
        log.info("Clearing cache '{}' after indexing", CacheNames.IMAGE_LIST);

        Cache imageListCache = cacheManager.getCache(CacheNames.IMAGE_LIST);
        if (imageListCache != null) {
            imageListCache.clear();
            log.info("Cache '{}' cleared successfully", CacheNames.IMAGE_LIST);
        } else {
            log.warn("Cache {} not found", CacheNames.IMAGE_LIST);
        }
    }

    /**
     * Generates a new timestamped index name
     * @param baseIndexName The base name for the index (e.g., "ocean-current-files")
     * @return A timestamped index name (e.g., "ocean-current-files-20251203t142530123")
     */
    private String generateTimestampedIndexName(String baseIndexName) {
        String timestamp = LocalDateTime.now().format(INDEX_TIMESTAMP_FORMAT);
        return baseIndexName + "-" + timestamp;
    }

    /**
     * Creates a new index with the given name and standard mappings
     * @param newIndexName The name of the index to create
     */
    private void createIndex(String newIndexName) throws IOException {
        CreateIndexRequest request = CreateIndexRequest.of(
                r -> r
                        .index(newIndexName)
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
        log.info("Created index '{}' with max_result_window of {}",
                newIndexName, esProperties.getMaxResultWindow());
    }

    /**
     * Checks if an alias exists
     * @param aliasName The name of the alias to check
     * @return true if the alias exists, false otherwise
     */
    private boolean isAliasExists(String aliasName) throws IOException {
        return esClient.indices().existsAlias(e -> e.name(aliasName)).value();
    }

    /**
     * Gets the list of indices currently pointed to by an alias
     * @param aliasName The name of the alias
     * @return Set of index names, or empty set if alias doesn't exist
     */
    private Set<String> getIndicesForAlias(String aliasName) throws IOException {
        if (!isAliasExists(aliasName)) {
            return Set.of();
        }

        GetAliasResponse response = esClient.indices().getAlias(g -> g.name(aliasName));
        return response.result().keySet();
    }

    /**
     * Atomically switches an alias from old indices to a new index.
     * This operation ensures zero downtime during reindexing.
     *
     * @param aliasName The alias name to switch
     * @param newIndexName The new index to point the alias to
     */
    private void switchAlias(String aliasName, String newIndexName) throws IOException {
        Set<String> oldIndices = getIndicesForAlias(aliasName);

        log.info("Switching alias '{}' from {} to '{}'",
                aliasName,
                oldIndices.isEmpty() ? "nothing" : oldIndices,
                newIndexName);

        esClient.indices().updateAliases(u -> {
            var builder = u;
            // Remove alias from all old indices
            for (String oldIndex : oldIndices) {
                builder = builder.actions(a -> a
                        .remove(r -> r
                                .index(oldIndex)
                                .alias(aliasName)
                        )
                );
            }
            // Add alias to new index
            builder = builder.actions(a -> a
                    .add(add -> add
                            .index(newIndexName)
                            .alias(aliasName)
                    )
            );
            return builder;
        });

        log.info("Successfully switched alias '{}' to index '{}'", aliasName, newIndexName);
    }

    /**
     * Deletes old indices that were previously pointed to by the alias.
     * This is called after successfully switching the alias to a new index.
     *
     * @param oldIndices Set of old index names to delete
     */
    private void deleteOldIndices(Set<String> oldIndices) {
        if (oldIndices.isEmpty()) {
            log.info("No old indices to delete");
            return;
        }

        for (String oldIndex : oldIndices) {
            try {
                esClient.indices().delete(d -> d.index(oldIndex));
                log.info("Deleted old index '{}'", oldIndex);
            } catch (Exception e) {
                log.error("Failed to delete old index '{}': {}", oldIndex, e.getMessage());
            }
        }
    }

    /**
     * Validates the new index before switching alias.
     * Checks:
     * 1. Document count is greater than 0
     * 2. Document count is at least the threshold percentage of current index (if alias exists)
     * 3. Distinct productId values match current index (if alias exists)
     *
     * @param newIndexName The name of the new index to validate
     * @param aliasName The alias name (e.g., "ocean-current-files")
     * @param callback Optional callback for progress updates
     * @throws ReindexValidationException if validation fails
     * @throws IOException if Elasticsearch operations fail
     */
    private void validateNewIndex(String newIndexName, String aliasName, IndexingCallback callback) throws IOException {
        log.info("Validating new index '{}' before switching alias '{}'", newIndexName, aliasName);

        long newIndexDocCount = getDocumentCount(newIndexName);
        log.info("New index '{}' contains {} documents", newIndexName, newIndexDocCount);

        // Check 1: Document count must be greater than 0
        if (newIndexDocCount == 0) {
            String errorMsg = String.format(
                    "New index '%s' contains 0 documents. Cannot switch alias to an empty index. Reindexing failed.",
                    newIndexName
            );
            log.error("[FATAL] {}", errorMsg);
            if (callback != null) {
                callback.onError(errorMsg);
            }
            throw new ReindexValidationException(errorMsg);
        }

        // Check 2 & 3: If alias exists, validate against current index
        if (isAliasExists(aliasName)) {
            // Get document count using the alias (which points to the current index)
            long currentIndexDocCount = getDocumentCount(aliasName);
            log.info("Current index (via alias '{}') contains {} documents", aliasName, currentIndexDocCount);

            if (currentIndexDocCount > 0) {
                // Check 2: Document count threshold
                double percentage = (newIndexDocCount * 100.0) / currentIndexDocCount;
                int threshold = esProperties.getReindexValidationThresholdPercent();

                log.info("New index has {}% of current index documents (threshold: {}%)",
                        percentage, threshold);

                if (percentage < threshold) {
                    String errorMsg = String.format(
                            "New index '%s' has only %.2f%% (%d) of current index documents (%d). " +
                            "Required threshold is %d%%. Cannot switch alias. Reindexing failed.",
                            newIndexName, percentage, newIndexDocCount, currentIndexDocCount, threshold
                    );
                    log.error("[FATAL] {}", errorMsg);
                    if (callback != null) {
                        callback.onError(errorMsg);
                    }
                    throw new ReindexValidationException(errorMsg);
                }

                log.info("Validation passed: New index has {}% of current index documents (threshold: {}%)",
                        percentage, threshold);
                if (callback != null) {
                    callback.onProgress(String.format(
                            "Validation passed: Document count check - new index has %.2f%% (%d/%d) documents (threshold: %d%%)",
                            percentage, newIndexDocCount, currentIndexDocCount, threshold
                    ));
                }

                // Check 3: Distinct productId values
                Set<String> currentProductIds = getDistinctProductIds(aliasName);
                Set<String> newProductIds = getDistinctProductIds(newIndexName);
                log.info("Current index has {} distinct productIds: {}", currentProductIds.size(), currentProductIds);
                log.info("New index has {} distinct productIds: {}", newProductIds.size(), newProductIds);

                // Check if any productIds are missing in new index
                Set<String> missingProductIds = new java.util.HashSet<>(currentProductIds);
                missingProductIds.removeAll(newProductIds);

                if (!missingProductIds.isEmpty()) {
                    String errorMsg = String.format(
                            "New index '%s' is missing productIds that exist in current index. " +
                            "Missing productIds: %s. This indicates data loss. Cannot switch alias. Reindexing failed.",
                            newIndexName, missingProductIds
                    );
                    log.error("[FATAL] {}", errorMsg);
                    if (callback != null) {
                        callback.onError(errorMsg);
                    }
                    throw new ReindexValidationException(errorMsg);
                }

                // Log any new productIds (this is expected and okay)
                Set<String> newlyAddedProductIds = new java.util.HashSet<>(newProductIds);
                newlyAddedProductIds.removeAll(currentProductIds);
                if (!newlyAddedProductIds.isEmpty()) {
                    log.info("New index contains additional productIds not in current index: {}", newlyAddedProductIds);
                }

                log.info("Validation passed: New index contains all productIds from current index ({}/{})",
                        newProductIds.size(), currentProductIds.size());
                if (callback != null) {
                    callback.onProgress(String.format(
                            "Validation passed: ProductId check - new index has all %d productIds from current index%s",
                            currentProductIds.size(),
                            newlyAddedProductIds.isEmpty() ? "" : " (plus " + newlyAddedProductIds.size() + " new ones)"
                    ));
                }
            }
        } else {
            log.info("Alias '{}' does not exist. Validation passed with {} documents in new index", aliasName, newIndexDocCount);

            // Still check and log distinct productIds for first time indexing
            Set<String> newProductIds = getDistinctProductIds(newIndexName);
            log.info("New index contains {} distinct productIds: {}", newProductIds.size(), newProductIds);

            if (callback != null) {
                callback.onProgress(String.format(
                        "Validation passed: New index contains %d documents with %d distinct productIds (first time indexing)",
                        newIndexDocCount, newProductIds.size()
                ));
            }
        }
    }

    /**
     * Gets the document count for a specific index.
     *
     * @param indexName The name of the index
     * @return The number of documents in the index
     * @throws IOException if the count request fails
     */
    private long getDocumentCount(String indexName) throws IOException {
        return esClient.count(c -> c.index(indexName)).count();
    }

    /**
     * Refreshes an index to make all recently indexed documents searchable.
     *
     * @param indexName The name of the index to refresh
     * @throws IOException if the refresh request fails
     */
    private void refreshIndex(String indexName) throws IOException {
        log.info("Refreshing index '{}'", indexName);
        esClient.indices().refresh(r -> r.index(indexName));
        log.info("Index '{}' refreshed successfully", indexName);
    }

    /**
     * Gets the set of distinct productIds in an index using terms aggregation.
     * Efficient for small sets (e.g., ~20 productIds).
     *
     * @param indexName The name of the index
     * @return Set of distinct productId values
     * @throws IOException if the aggregation request fails
     */
    private Set<String> getDistinctProductIds(String indexName) throws IOException {
        var response = esClient.search(s -> s
                .index(indexName)
                .size(0)
                .aggregations("unique_products", a -> a
                        .terms(t -> t
                                .field("productId")
                                .size(100)  // Large enough for all productIds
                        )
                ),
                ImageMetadataEntry.class
        );

        return response.aggregations()
                .get("unique_products")
                .sterms()
                .buckets()
                .array()
                .stream()
                .map(bucket -> bucket.key().stringValue())
                .collect(java.util.stream.Collectors.toSet());
    }
}
