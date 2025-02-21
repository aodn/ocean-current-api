package au.org.aodn.oceancurrent.service;

import au.org.aodn.oceancurrent.configuration.AppConstants;
import au.org.aodn.oceancurrent.model.ImageMetadataEntry;
import au.org.aodn.oceancurrent.model.ImageMetadataGroup;
import au.org.aodn.oceancurrent.util.converter.ImageMetadataConverter;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.json.JsonData;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
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

    private static final String FIELD_PRODUCT = "product";
    private static final String FIELD_SUB_PRODUCT = "subProduct";
    private static final String FIELD_REGION = "region";
    private static final String FIELD_FILE_NAME = "fileName";

    private static final String AGG_LESS_THAN_TARGET = "less_than_target";
    private static final String AGG_TOP_LESS = "top_less";
    private static final String AGG_GREATER_THAN_TARGET = "greater_than_target";
    private static final String AGG_TOP_GREATER = "top_greater";

    private final ElasticsearchClient esClient;
    private final ObjectMapper objectMapper;

    public ImageMetadataGroup findByProductAndRegion(String product, String region) throws IOException {
        Query query = QueryBuilders.bool()
                .must(QueryBuilders.term(t -> t.field(FIELD_PRODUCT).value(product)))
                .must(QueryBuilders.term(t -> t.field(FIELD_REGION).value(region)))
                .build()._toQuery();

        SearchResponse<ImageMetadataEntry> response = esClient.search(s -> s
                        .index(indexName)
                        .query(query),
                ImageMetadataEntry.class
        );

        return ImageMetadataConverter.toGroup(extractHits(response));
    }

    public ImageMetadataGroup findByProductRegionAndDateRange(
            String product,
            String subProduct,
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
                                                        .field(FIELD_PRODUCT)
                                                        .value(product)
                                                )
                                        )
                                        .must(m -> m
                                                .term(t -> t
                                                        .field(FIELD_SUB_PRODUCT)
                                                        .value(subProduct)
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

        return ImageMetadataConverter.toGroup(flatResults);
    }

    public ImageMetadataGroup searchFilesAroundDate(
            String product,
            String subProduct,
            String region,
            String date,
            int size) {
        log.info("Executing search operation for product: {}, subProduct: {}, region: {}, date: {}",
                product, subProduct, region, date);
        try {
            SearchResponse<Void> responseVoid = esClient.search(s -> s
                            .index(indexName)
                            .size(0)
                            .query(q -> q.bool(b -> b
                                    .must(
                                            m -> m.term(t -> t.field(FIELD_PRODUCT).value(product))

                                    )
                                    .must(
                                            m -> m.term(t -> t.field(FIELD_SUB_PRODUCT).value(subProduct))
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

            return ImageMetadataConverter.toGroup(combinedSortedResults);
        } catch (Exception e) {
            log.error("Search operation failed - product: {}, subProduct: {}, region: {}, date: {}",
                    product, subProduct, region, date);
            throw new RuntimeException(e);
        }
    }

    public ImageMetadataGroup getImageMetadata(String product, String subProduct,
                                               String region, String date, int size) {
        List<ImageMetadataEntry> beforeDocs = getDocumentsBeforeDate(product, subProduct, region, date, size);
        List<ImageMetadataEntry> afterDocs = getDocumentsAfterDate(product, subProduct, region, date, size);

        List<ImageMetadataEntry> allDocuments = new ArrayList<>();
        allDocuments.addAll(beforeDocs);
        allDocuments.addAll(afterDocs);

        List<ImageMetadataEntry> sortedDocuments = allDocuments.stream()
                .sorted(Comparator.comparing(ImageMetadataEntry::getFileName))
                .collect(Collectors.toList());

        return ImageMetadataConverter.toGroup(sortedDocuments);
    }

    private List<ImageMetadataEntry> getDocumentsBeforeDate(String product, String subProduct,
                                                  String region, String date, int size) {
        try {
            SearchResponse<ImageMetadataEntry> response = esClient.search(s -> s
                            .index(indexName)
                            .size(size)
                            .query(q -> q
                                    .bool(b -> b
                                            .must(m -> m.term(t -> t.field(FIELD_PRODUCT).value(product)))
                                            .must(m -> m.term(t -> t.field(FIELD_SUB_PRODUCT).value(subProduct)))
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

    private List<ImageMetadataEntry> getDocumentsAfterDate(String product, String subProduct,
                                                 String region, String date, int size) {
        try {
            SearchResponse<ImageMetadataEntry> response = esClient.search(s -> s
                            .index(indexName)
                            .size(size)
                            .query(q -> q
                                    .bool(b -> b
                                            .must(m -> m.term(t -> t.field(FIELD_PRODUCT).value(product)))
                                            .must(m -> m.term(t -> t.field(FIELD_SUB_PRODUCT).value(subProduct)))
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
}
