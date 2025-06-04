package au.org.aodn.oceancurrent.controller;

import au.org.aodn.oceancurrent.service.IndexingService;
import au.org.aodn.oceancurrent.util.elasticsearch.BulkRequestProcessor;
import au.org.aodn.oceancurrent.util.elasticsearch.IndexingCallback;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.CompletableFuture;

@Slf4j
@RestController
@RequestMapping("/s3")
@RequiredArgsConstructor
@Tag(name = "S3 Indexing", description = "Endpoints for managing S3 file indexing")
public class S3IndexingController {
    private final IndexingService indexingService;
    private static final int BATCH_SIZE = 100000;

    @PostMapping("/index")
    @Operation(summary = "Trigger S3 waves files indexing", description = "Indexes waves files from S3 bucket into Elasticsearch")
    public ResponseEntity<String> triggerS3Indexing() {
        log.info("Received request to trigger S3 waves files indexing");

        // Run indexing asynchronously
        CompletableFuture.runAsync(() -> {
            try {
                BulkRequestProcessor processor = new BulkRequestProcessor(BATCH_SIZE, indexingService.getIndexName(), indexingService.getEsClient());
                IndexingCallback callback = new IndexingCallback() {
                    @Override
                    public void onProgress(String message) {
                        log.info("S3 Indexing Progress: {}", message);
                    }

                    @Override
                    public void onComplete(String message) {
                        log.info("S3 Indexing Complete: {}", message);
                    }

                    @Override
                    public void onError(String error) {
                        log.error("S3 Indexing Error: {}", error);
                    }
                };

                int indexedCount = indexingService.indexS3WavesFiles(processor, callback);
                log.info("Successfully indexed {} waves files from S3", indexedCount);
            } catch (Exception e) {
                log.error("Failed to index S3 waves files", e);
            }
        });

        return ResponseEntity.accepted().body("S3 waves files indexing started");
    }
}
