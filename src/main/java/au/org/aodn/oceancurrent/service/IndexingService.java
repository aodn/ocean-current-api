package au.org.aodn.oceancurrent.service;

import au.org.aodn.oceancurrent.configuration.AppConstants;
import au.org.aodn.oceancurrent.model.ImageMetadataEntry;
import au.org.aodn.oceancurrent.model.ImageMetadataGroup;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.DeleteByQueryRequest;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class IndexingService {
    private final String indexName = AppConstants.INDEX_NAME;

    @Value("${json.file.path:data/STATE_daily/SST}")
    private String jsonFilePath;

    private final ElasticsearchClient esClient;
    private final ObjectMapper objectMapper;

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

        if (exists) {
            esClient.indices().delete(c -> c.index(indexName));
            log.info("Index with name '{}' deleted", indexName);
        }
    }

    public void indexJsonFiles() throws IOException {
        // Ensure index exists
        createIndexIfNotExists();

        Resource resource = new ClassPathResource(jsonFilePath);
        if (!resource.exists()) {
            throw new IOException("Resource not found: " + jsonFilePath);
        }

        log.info("Indexing JSON file: {}", jsonFilePath);
        try {
            String jsonContext = new String(resource.getInputStream().readAllBytes());
            List<ImageMetadataGroup> imageMetadataGroups = objectMapper.readValue(jsonContext,
                    new TypeReference<>() {
                    });

            // Delete existing documents
            DeleteByQueryRequest deleteRequest = DeleteByQueryRequest.of(d -> d
                    .index(indexName)
                    .query(q -> q.matchAll(m -> m))
            );
            log.info("Deleting existing documents");
            esClient.deleteByQuery(deleteRequest);
            log.info("Existing documents deleted");

            // Bulk index new documents
            BulkRequest.Builder bulkRequest = new BulkRequest.Builder();
            log.info("Bulk Indexing new documents");
            for (ImageMetadataGroup imageMetadataGroup : imageMetadataGroups) {

                String product = imageMetadataGroup.getProduct();
                String subProduct = imageMetadataGroup.getSubProduct();
                String region = imageMetadataGroup.getRegion();
                String productPath = imageMetadataGroup.getPath();

                imageMetadataGroup.getFiles().forEach(file -> {
                    ImageMetadataEntry doc = new ImageMetadataEntry();
                    doc.setProduct(product);
                    doc.setSubProduct(subProduct);
                    doc.setRegion(region);
                    doc.setPath(productPath);
                    doc.setFileName(file.getName());
                    doc.setFilePath(file.getPath());

                    bulkRequest.operations(op -> op
                            .index(idx -> idx
                                    .index(indexName)
                                    .document(doc)
                            )
                    );
                });
            }

            BulkResponse bulkResponse = esClient.bulk(bulkRequest.build());
            if (bulkResponse.errors()) {
                log.error("Bulk indexing has errors!");
                bulkResponse.items().forEach(item -> {
                    if (item.error() != null) {
                        log.error("Error for item: {}", item.error().reason());
                    }
                });
            }

            log.info("Indexing with name '{}' completed", indexName);
        } catch (IOException e) {
            log.error("Error during indexing: {}", e.getMessage());
            throw e;
        }
    }

    private boolean isIndexExists() throws IOException {
        return esClient.indices()
                .exists(c -> c.index(indexName))
                .value();
    }
}
