package au.org.aodn.oceancurrent.service;

import au.org.aodn.oceancurrent.configuration.AppConstants;
import au.org.aodn.oceancurrent.constant.CacheNames;
import au.org.aodn.oceancurrent.dto.CurrentMetersPlotResponse;
import au.org.aodn.oceancurrent.dto.RegionLatestFileResponse;
import au.org.aodn.oceancurrent.exception.ResourceNotFoundException;
import au.org.aodn.oceancurrent.model.ImageMetadataEntry;
import au.org.aodn.oceancurrent.model.ImageMetadataGroup;
import au.org.aodn.oceancurrent.util.ProductIdUtils;
import au.org.aodn.oceancurrent.util.converter.ImageMetadataConverter;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.json.JsonData;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchService {
    private final String indexName = AppConstants.INDEX_NAME;

    private static final String FIELD_PRODUCT_ID = "productId";
    private static final String FIELD_REGION = "region";
    private static final String FIELD_FILE_NAME = "fileName";
    private static final String FIELD_DEPTH = "depth";
    private static final String FIELD_PATH = "path";

    private static final String AGG_LESS_THAN_TARGET = "less_than_target";
    private static final String AGG_TOP_LESS = "top_less";
    private static final String AGG_GREATER_THAN_TARGET = "greater_than_target";
    private static final String AGG_TOP_GREATER = "top_greater";
    private static final String AGG_LATEST_FILES = "latest_files";
    private static final String AGG_TOP_HITS = "top_hits";

    private static final String PRODUCT_TYPE_CURRENT_METERS_PLOT = "currentMetersPlot";

    private final ElasticsearchClient esClient;
    private final ObjectMapper objectMapper;

    public ImageMetadataGroup findByProductAndRegion(String productId, String region) throws IOException {
        Query query = QueryBuilders.bool()
                .must(QueryBuilders.term(t -> t.field(FIELD_PRODUCT_ID).value(productId)))
                .must(QueryBuilders.term(t -> t.field(FIELD_REGION).value(region)))
                .build()._toQuery();

        SearchResponse<ImageMetadataEntry> response = esClient.search(s -> s
                        .index(indexName)
                        .query(query),
                ImageMetadataEntry.class
        );

        return ImageMetadataConverter.toMetadataGroup(extractHits(response), productId, region);
    }

    public ImageMetadataGroup findByProductRegionAndDateRange(
            String productId,
            String region,
            String fromDate,
            String toDate,
            int size) throws IOException {
        SearchResponse<ImageMetadataEntry> response = esClient.search(s -> s
                        .index(indexName)
                        .query(q -> q
                                .bool(b -> b
                                        .must(m -> m
                                                .term(t -> t
                                                        .field(FIELD_PRODUCT_ID)
                                                        .value(productId)
                                                )
                                        )
                                        .must(m -> m
                                                .term(t -> t
                                                        .field(FIELD_REGION)
                                                        .value(region)
                                                )
                                        )
                                        .must(m -> m
                                                .range(r -> r
                                                        .term(r1 -> r1
                                                                .field(FIELD_FILE_NAME)
                                                                .gte(fromDate)
                                                                .lt(toDate)))
                                        )
                                )
                        )
                        .sort(st -> st
                                .field(f -> f
                                        .field(FIELD_FILE_NAME)
                                        .order(SortOrder.Asc)
                                )
                        )
                        .size(size),
                ImageMetadataEntry.class
        );

        List<ImageMetadataEntry> flatResults = response.hits().hits()
                .stream()
                .map(Hit::source)
                .collect(Collectors.toList());

        return ImageMetadataConverter.toMetadataGroup(flatResults, productId, region);
    }

    public ImageMetadataGroup searchFilesAroundDate(
            String productId,
            String region,
            String date,
            int size) {
        log.info("Executing search operation for product: {}, region: {}, date: {}",
                productId, region, date);
        try {
            SearchResponse<Void> responseVoid = esClient.search(s -> s
                            .index(indexName)
                            .size(0)
                            .query(q -> q.bool(b -> b
                                    .must(
                                            m -> m.term(t -> t.field(FIELD_PRODUCT_ID).value(productId))

                                    )
                                    .must(
                                            m -> m.term(t -> t.field(FIELD_REGION).value(region))
                                    )
                            ))
                            .aggregations(AGG_LESS_THAN_TARGET, a -> a
                                    .filter(f -> f
                                            .range(r -> r
                                                    .term(r1 -> r1
                                                            .field(FIELD_FILE_NAME)
                                                            .lt(date)
                                                    ))
                                    )
                                    .aggregations(AGG_TOP_LESS, a2 -> a2
                                            .topHits(th -> th
                                                    .size(size)
                                                    .sort(srt -> srt.field(f -> f
                                                            .field(FIELD_FILE_NAME)
                                                            .order(SortOrder.Desc)
                                                    ))
                                            )
                                    )
                            )
                            .aggregations(AGG_GREATER_THAN_TARGET, a -> a
                                    .filter(f -> f
                                            .range(r -> r
                                                    .term(r1 -> r1
                                                            .field(FIELD_FILE_NAME)
                                                            .gt(date)
                                                    ))
                                    )
                                    .aggregations(AGG_TOP_GREATER, a2 -> a2
                                            .topHits(th -> th
                                                    .size(size)
                                                    .sort(srt -> srt.field(f -> f
                                                            .field(FIELD_FILE_NAME)
                                                            .order(SortOrder.Asc)
                                                    ))
                                            )
                                    )
                            ),
                    Void.class);

            List<ImageMetadataEntry> beforeDateResults = extractImageMetadataEntries(
                    responseVoid.aggregations(),
                    AGG_LESS_THAN_TARGET,
                    AGG_TOP_LESS);
            List<ImageMetadataEntry> afterDateResults = extractImageMetadataEntries(
                    responseVoid.aggregations(),
                    AGG_GREATER_THAN_TARGET,
                    AGG_TOP_GREATER);

            List<ImageMetadataEntry> combinedSortedResults = Stream
                    .concat(beforeDateResults.stream(), afterDateResults.stream())
                    .sorted(Comparator.comparing(ImageMetadataEntry::getFileName))
                    .toList();

            log.info("Search operation completed - found {} results ({} before, {} after target date)",
                    combinedSortedResults.size(), beforeDateResults.size(), afterDateResults.size());

            return ImageMetadataConverter.toMetadataGroup(combinedSortedResults, productId, region);
        } catch (Exception e) {
            log.error("Search operation failed - product: {}, region: {}, date: {}",
                    productId, region, date);
            throw new RuntimeException(e);
        }
    }

    public ImageMetadataGroup getImageMetadata(String productId, String region, String date, int size) {
        List<ImageMetadataEntry> beforeDocs = getDocumentsBeforeDate(productId, region, date, size);
        List<ImageMetadataEntry> afterDocs = getDocumentsAfterDate(productId, region, date, size);

        List<ImageMetadataEntry> allDocuments = new ArrayList<>();
        allDocuments.addAll(beforeDocs);
        allDocuments.addAll(afterDocs);

        List<ImageMetadataEntry> sortedDocuments = allDocuments.stream()
                .sorted(Comparator.comparing(ImageMetadataEntry::getFileName))
                .collect(Collectors.toList());

        return ImageMetadataConverter.toMetadataGroup(sortedDocuments, productId, region);
    }

    @Cacheable(value = CacheNames.IMAGE_LIST, key = "{#productId, #region, #depth}")
    public List<ImageMetadataGroup> findAllImageList(String productId, String region, String depth) {
        try {
            // Validate parameters
            boolean isValidRegion = isValidParameter(region);
            boolean isValidDepth = isValidParameter(depth);
            BoolQuery.Builder queryBuilder = new BoolQuery.Builder()
                    .must(t -> t.term(f -> f.field(FIELD_PRODUCT_ID).value(productId)));

            // Add region filter if provided
            if (isValidRegion) {
                queryBuilder.must(t -> t.term(f -> f.field(FIELD_REGION).value(region)));
            }

            // Add a depth filter if provided
            if (isValidDepth) {
                queryBuilder.must(t -> t.term(f -> f.field(FIELD_DEPTH).value(depth)));
            }

            SearchResponse<ImageMetadataEntry> response = esClient.search(s -> s
                            .index(indexName)
                            .size(20000)
                            .query(q -> q.bool(queryBuilder.build())),
                    ImageMetadataEntry.class
            );

            List<ImageMetadataEntry> entries = extractHits(response);

            log.info("Found {} images for product '{}'{} {}",
                    entries.size(), productId,
                    isValidRegion ? ", region '" + region + "'" : "",
                    isValidDepth? ", depth '" + depth + "'" : "");

            return ImageMetadataConverter.createMetadataGroups(entries);
        } catch (Exception e) {
            log.error("Error fetching image metadata", e);
            throw new RuntimeException(e);
        }
    }

    @Cacheable(value = CacheNames.LATEST_FILES, key = "#productId")
    public List<RegionLatestFileResponse> findLatestFileNameByRegion(String productId) {
        try {
            SearchResponse<Void> response = esClient.search(s -> s
                            .index(indexName)
                            .size(0)
                            .query(q -> q
                                .bool(b -> b
                                        .must(m -> m
                                                .term(t -> t
                                                        .field(FIELD_PRODUCT_ID)
                                                        .value(productId)
                                                )
                                        )
                                        .must(m -> m
                                                .regexp(r -> r
                                                        .field(FIELD_FILE_NAME)
                                                        .value("\\d{8,14}\\.gif")
                                                )
                                        )
                                )
                        )
                        .aggregations(AGG_LATEST_FILES, a -> a
                                .terms(t -> t
                                        .field(FIELD_REGION)
                                        .size(1000)
                                )
                                .aggregations(AGG_TOP_HITS, a2 -> a2
                                        .topHits(th -> th
                                                .size(1)
                                                .sort(srt -> srt
                                                        .field(f -> f
                                                                .field(FIELD_FILE_NAME)
                                                                .order(SortOrder.Desc)
                                                        )
                                                )
                                        )
                                )
                        ),
                Void.class
        );

        List<RegionLatestFileResponse> result = new ArrayList<>();

        response.aggregations()
                .get(AGG_LATEST_FILES)
                .sterms()
                .buckets()
                .array()
                .forEach(bucket -> {
                    String region = bucket.key().stringValue();
                    JsonData source = bucket.aggregations()
                            .get(AGG_TOP_HITS)
                            .topHits()
                            .hits()
                            .hits()
                            .get(0)
                            .source();
                    if (source == null) {
                        log.warn("No source found for region: {}", region);
                        return;
                    }
                    String latestFileName = source.toJson().asJsonObject().getString(FIELD_FILE_NAME);
                    String path = source.toJson().asJsonObject().getString(FIELD_PATH);
                    String sourceProductId = source.toJson().asJsonObject().getString(FIELD_PRODUCT_ID);
                    result.add(new RegionLatestFileResponse(sourceProductId, region, latestFileName, path));
                });

            log.info("Found {} latest files for product: {}", result.size(), productId);

            return result;
        } catch (Exception e) {
            log.error("Error fetching latest files", e);
            throw new RuntimeException("Failed to retrieve latest files", e);
        }
    }

    @Cacheable(value = CacheNames.CURRENT_METERS_PLOT_LIST, key = "{#plotName}")
    public CurrentMetersPlotResponse findLatestCurrentMetersPlotByPlotName(String plotName) {
        try {
            BoolQuery.Builder queryBuilder = new BoolQuery.Builder()
                    .must(t -> t.prefix(f -> f.field(FIELD_PRODUCT_ID).value(PRODUCT_TYPE_CURRENT_METERS_PLOT)))
                    .must(t -> t.term(f -> f.field(FIELD_REGION).value(plotName)));

            SearchResponse<ImageMetadataEntry> response = esClient.search(s -> s
                            .index(indexName)
                            .size(20000)
                            .query(q -> q.bool(queryBuilder.build())),
                    ImageMetadataEntry.class
            );

            List<ImageMetadataEntry> allEntries = extractHits(response);

            if (allEntries.isEmpty()) {
                log.info("No current meters plot data found with plot name: {}", plotName);
                throw new ResourceNotFoundException("Current meters image", "plot name = " + plotName);
            }

            Integer highestVersion = ProductIdUtils.findHighestVersionNumber(allEntries, PRODUCT_TYPE_CURRENT_METERS_PLOT);

            if (highestVersion == null) {
                log.info("No valid current meters plot versions found with plot name: {}", plotName);
                throw new ResourceNotFoundException("Current meters image", "plot name = " + plotName);
            }

            String highestVersionProductId = PRODUCT_TYPE_CURRENT_METERS_PLOT + "-" + highestVersion;
            List<ImageMetadataEntry> highestVersionEntries = allEntries.stream()
                    .filter(entry -> highestVersionProductId.equals(entry.getProductId()))
                    .collect(Collectors.toList());

            log.info("Found {} images for the product '{}' with plot '{}'",
                    highestVersionEntries.size(),
                    highestVersionProductId,
                    plotName);

            return ImageMetadataConverter.createCurrentMetersPlotResponse(highestVersionEntries);

        } catch (ResourceNotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error fetching current meters plot data", e);
            throw new RuntimeException("Failed to retrieve current meters plot data", e);
        }
    }

    private List<ImageMetadataEntry> getDocumentsBeforeDate(String productId, String region, String date, int size) {
        try {
            SearchResponse<ImageMetadataEntry> response = esClient.search(s -> s
                            .index(indexName)
                            .size(size)
                            .query(q -> q
                                    .bool(b -> b
                                            .must(m -> m.term(t -> t.field(FIELD_PRODUCT_ID).value(productId)))
                                            .must(m -> m.term(t -> t.field(FIELD_REGION).value(region)))
                                            .must(m -> m.range(r -> r
                                                    .term(r1 -> r1
                                                            .field(FIELD_FILE_NAME)
                                                            .lte(date)
                                                    )
                                            ))
                                    )
                            )
                            .sort(srt -> srt
                                    .field(f -> f
                                            .field(FIELD_FILE_NAME)
                                            .order(SortOrder.Desc)
                                    )
                            ),
                    ImageMetadataEntry.class
            );

            log.debug("Found {} documents before date {}", response.hits().hits().size(), date);

            return response.hits().hits().stream()
                    .map(Hit::source)
                    .collect(Collectors.toList());
        } catch (IOException e) {
            log.error("Error executing Elasticsearch query for documents before date", e);
            throw new RuntimeException(e);
        }
    }

    private List<ImageMetadataEntry> getDocumentsAfterDate(String productId, String region, String date, int size) {
        try {
            SearchResponse<ImageMetadataEntry> response = esClient.search(s -> s
                            .index(indexName)
                            .size(size)
                            .query(q -> q
                                    .bool(b -> b
                                            .must(m -> m.term(t -> t.field(FIELD_PRODUCT_ID).value(productId)))
                                            .must(m -> m.term(t -> t.field(FIELD_REGION).value(region)))
                                            .must(m -> m.range(r -> r
                                                    .term(r1 -> r1
                                                            .field(FIELD_FILE_NAME)
                                                            .gte(date)
                                                    )
                                            ))
                                    )
                            )
                            .sort(srt -> srt
                                    .field(f -> f
                                            .field(FIELD_FILE_NAME)
                                            .order(SortOrder.Asc)
                                    )
                            ),
                    ImageMetadataEntry.class
            );

            log.debug("Found {} documents after date {}", response.hits().hits().size(), date);

            return response.hits().hits().stream()
                    .map(Hit::source)
                    .collect(Collectors.toList());
        } catch (IOException e) {
            log.error("Error executing Elasticsearch query for documents after date", e);
            throw new RuntimeException(e);
        }
    }
    private List<ImageMetadataEntry> extractHits(SearchResponse<ImageMetadataEntry> response) {
        if (response.hits().hits().isEmpty()) {
            return Collections.emptyList();
        }
        return response.hits().hits().stream()
                .map(Hit::source)
                .collect(Collectors.toList());
    }

    private ImageMetadataEntry extractSourceFromAggregation(Hit<JsonData> hit) {
        try {
            JsonData hitSource = hit.source();
            String sourceString = (hitSource != null) ? hitSource.toJson().toString() : null;
            return objectMapper.readValue(sourceString, ImageMetadataEntry.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    private List<ImageMetadataEntry> extractImageMetadataEntries(Map<String, Aggregate> aggregations, String targetKey, String topKey) {
        return aggregations
                .get(targetKey)
                .filter()
                .aggregations()
                .get(topKey)
                .topHits()
                .hits()
                .hits()
                .stream()
                .map(this::extractSourceFromAggregation)
                .toList();
    }

    private boolean isValidParameter(String value) {
        return value != null && !value.isBlank();
    }
}
