package au.org.aodn.oceancurrent;

import au.org.aodn.oceancurrent.model.ImageMetadataEntry;
import co.elastic.clients.elasticsearch.core.IndexRequest;
import co.elastic.clients.elasticsearch.core.IndexResponse;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.TotalHits;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@Testcontainers
public class SampleElasticsearchTest extends ElasticsearchTestBase {
        private static final Logger log = LoggerFactory.getLogger(SampleElasticsearchTest.class);

        @Override
        protected String getTestIndexName() {
            return "test_documents";
        }

        /**
         * Example of extending the index mapping with additional fields specific to this test
         */
        @Override
        protected void addCustomMappingProperties(CreateIndexRequest.Builder builder) {
            // Add custom field mappings specific to this test case
            builder.mappings(m -> m
                    .properties("contentType", p -> p.keyword(k -> k))
                    .properties("fileSize", p -> p.long_(l -> l))
                    .properties("tags", p -> p.keyword(k -> k))
            );
        }

        @Test
        @DisplayName("Should index and search a document with Testcontainers")
        void testIndexAndSearchDocument() throws IOException {
            // Log the Elasticsearch container status
            log.info("Elasticsearch container is running: {}", elasticsearchContainer.isRunning());
            log.info("Elasticsearch URL: {}", elasticsearchContainer.getHttpHostAddress());

            // Create a test document using ImageMetadataEntry
            String documentId = UUID.randomUUID().toString();
            ImageMetadataEntry document = new ImageMetadataEntry();
            document.setPath("/test/path");
            document.setProductId("product123");
            document.setRegion("AUS");
            document.setFileName("test.json");
            // Set other fields as needed

            // Index the document
            IndexResponse indexResponse = esClient.index(IndexRequest.of(i -> i
                    .index(getTestIndexName())
                    .id(documentId)
                    .document(document)
            ));

            // Ensure document was indexed successfully
            assertEquals(documentId, indexResponse.id());
            assertNotNull(indexResponse.result());

            // Refresh the index to make changes visible
            esClient.indices().refresh(r -> r.index(getTestIndexName()));

            // Search for the document
            SearchResponse<ImageMetadataEntry> searchResponse = esClient.search(SearchRequest.of(s -> s
                    .index(getTestIndexName())
                    .query(q -> q
                            .term(t -> t
                                    .field("productId")
                                    .value("product123")
                            )
                    )
            ), ImageMetadataEntry.class);

            // Verify search results
            TotalHits totalHits = searchResponse.hits().total();
            assertNotNull(totalHits);
            assertEquals(1, totalHits.value());

            List<Hit<ImageMetadataEntry>> hits = searchResponse.hits().hits();
            assertEquals(1, hits.size());

            ImageMetadataEntry foundDocument = hits.get(0).source();
            assertNotNull(foundDocument);
            assertEquals("/test/path", foundDocument.getPath());
            assertEquals("product123", foundDocument.getProductId());
            assertEquals("AUS", foundDocument.getRegion());
            assertEquals("test.json", foundDocument.getFileName());
        }

        @Test
        @DisplayName("Should filter documents by region and productId")
        void testFilterByFields() throws IOException {
            // Index documents with different regions and productIds
            indexTestDocument("doc1", "AUS", "product1");
            indexTestDocument("doc2", "NZ", "product1");
            indexTestDocument("doc3",  "AUS", "product2");

            // Refresh the index
            esClient.indices().refresh(r -> r.index(getTestIndexName()));

            // Search for documents from Australia
            SearchResponse<ImageMetadataEntry> regionSearchResponse = esClient.search(SearchRequest.of(s -> s
                    .index(getTestIndexName())
                    .query(q -> q
                            .term(t -> t
                                    .field("region")
                                    .value("AUS")
                            )
                    )
            ), ImageMetadataEntry.class);

            // Verify response structure before assertions
            assertNotNull(regionSearchResponse, "Search response should not be null");
            assertNotNull(regionSearchResponse.hits(), "Search hits should not be null");
            assertNotNull(regionSearchResponse.hits().total(), "Total hits should not be null");

            // Should find 2 documents (doc1 and doc3)
            assertEquals(2, regionSearchResponse.hits().total().value());

            // Search for documents with product1
            SearchResponse<ImageMetadataEntry> productSearchResponse = esClient.search(SearchRequest.of(s -> s
                    .index(getTestIndexName())
                    .query(q -> q
                            .term(t -> t
                                    .field("productId")
                                    .value("product1")
                            )
                    )
            ), ImageMetadataEntry.class);

            // Verify response structure
            assertNotNull(productSearchResponse.hits());
            assertNotNull(productSearchResponse.hits().total());

            // Should find 2 documents (doc1 and doc2)
            assertEquals(2, productSearchResponse.hits().total().value());

            // Combined search: Australia AND product1
            SearchResponse<ImageMetadataEntry> combinedSearchResponse = esClient.search(SearchRequest.of(s -> s
                    .index(getTestIndexName())
                    .query(q -> q
                            .bool(b -> b
                                    .must(m -> m
                                            .term(t -> t
                                                    .field("region")
                                                    .value("AUS")
                                            )
                                    )
                                    .must(m -> m
                                            .term(t -> t
                                                    .field("productId")
                                                    .value("product1")
                                            )
                                    )
                            )
                    )
            ), ImageMetadataEntry.class);

            // Verify response structure
            assertNotNull(combinedSearchResponse.hits());
            assertNotNull(combinedSearchResponse.hits().total());

            // Should find 1 document (doc1)
            assertEquals(1, combinedSearchResponse.hits().total().value());

            // Verify hits list is not empty
            assertNotNull(combinedSearchResponse.hits().hits());
            assertFalse(combinedSearchResponse.hits().hits().isEmpty(), "Hits list should not be empty");

            // Verify first hit has source
            Hit<ImageMetadataEntry> firstHit = combinedSearchResponse.hits().hits().get(0);
            assertNotNull(firstHit, "First hit should not be null");
            assertNotNull(firstHit.source(), "Hit source should not be null");

            // Verify productId
            assertEquals("product1", firstHit.source().getProductId());
        }

        /**
         * Helper method to index a test document with region and productId
         */
        private void indexTestDocument(String id, String region, String productId) throws IOException {
            ImageMetadataEntry document = new ImageMetadataEntry();
            document.setPath("/test/" + id);
            document.setProductId(productId);
            document.setRegion(region);
            document.setFileName(id + ".json");
            // Set other fields as needed

            esClient.index(IndexRequest.of(i -> i
                    .index(getTestIndexName())
                    .id(id)
                    .document(document)
            ));
        }
    }
