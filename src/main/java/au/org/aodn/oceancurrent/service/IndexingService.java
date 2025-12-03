package au.org.aodn.oceancurrent.service;

import au.org.aodn.oceancurrent.configuration.elasticsearch.ElasticsearchProperties;
import au.org.aodn.oceancurrent.constant.CacheNames;
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
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static au.org.aodn.oceancurrent.constant.ProductConstants.PRODUCT_ID_MAPPINGS;

@Service
@Slf4j
public class IndexingService {
    private final String indexName;
    private static final int BATCH_SIZE = 100000;
    private static final int THREAD_POOL_SIZE = 2;
    private static final DateTimeFormatter INDEX_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final Pattern INDEX_VERSION_PATTERN = Pattern.compile("^(.+)-(\\d{4}-\\d{2}-\\d{2})-v(\\d+)$");

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
        this.indexName = esProperties.getIndexName();
        this.cacheManager = cacheManager;
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

//    /**
//     * @deprecated Use reindexAll() instead for complete reindexing with zero downtime.
//     * This method is kept for backward compatibility only.
//     */
//    @Deprecated
//    public void indexRemoteJsonFiles(boolean confirm) throws IOException {
//        reindexAll(confirm, null);
//    }
//
//    /**
//     * @deprecated Use reindexAll() instead for complete reindexing with zero downtime.
//     * This method is kept for backward compatibility only.
//     */
//    @Deprecated
//    @CacheEvict(value = CacheNames.IMAGE_LIST, allEntries = true)
//    public void indexRemoteJsonFiles(boolean confirm, IndexingCallback callback) throws IOException {
//        log.warn("indexRemoteJsonFiles() is deprecated. Calling reindexAll() instead.");
//        reindexAll(confirm, callback);
//    }
//
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
        String newIndexName = generateTimestampedIndexName(indexName);
        log.info("Creating new index: {}", newIndexName);

        // Get old indices before creating new one
        Set<String> oldIndices = getIndicesForAlias(indexName);

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

            // Atomically switch the alias from old index to new index
            switchAlias(indexName, newIndexName);
            if (callback != null) {
                callback.onProgress("Switched alias '" + indexName + "' to new index");
            }

            // Delete old indices
            deleteOldIndices(oldIndices);
            if (callback != null) {
                callback.onProgress("Cleaned up old indices");
            }

            if (callback != null) {
                callback.onComplete("Full reindexing completed successfully with zero downtime");
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
     * This is a helper method used by both indexS3SurfaceWavesFiles and reindexAll.
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
        return isIndexExists(indexName);
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
     * Generates a new versioned index name with today's date
     * @param baseIndexName The base name for the index (e.g., "ocean-current-files")
     * @return A versioned index name (e.g., "ocean-current-files-2025-12-03-v1")
     */
    private String generateTimestampedIndexName(String baseIndexName) throws IOException {
        String today = LocalDate.now().format(INDEX_DATE_FORMAT);
        int nextVersion = getNextVersionForDate(baseIndexName, today);
        return String.format("%s-%s-v%d", baseIndexName, today, nextVersion);
    }

    /**
     * Finds the next available version number for a given date by querying existing indices
     * @param baseIndexName The base name for the index
     * @param date The date string in yyyy-MM-dd format
     * @return The next version number (starting from 1)
     */
    private int getNextVersionForDate(String baseIndexName, String date) throws IOException {
        // Get all indices matching the pattern baseIndexName-*
        var response = esClient.indices().get(g -> g.index(baseIndexName + "-*"));

        int maxVersion = 0;

        for (String indexName : response.result().keySet()) {
            Matcher matcher = INDEX_VERSION_PATTERN.matcher(indexName);
            if (matcher.matches()) {
                String indexBase = matcher.group(1);
                String indexDate = matcher.group(2);
                int version = Integer.parseInt(matcher.group(3));

                // Check if this index matches our base name and date
                if (indexBase.equals(baseIndexName) && indexDate.equals(date)) {
                    maxVersion = Math.max(maxVersion, version);
                }
            }
        }

        return maxVersion + 1;
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
}
