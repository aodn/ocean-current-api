package au.org.aodn.oceancurrent.controller;

import au.org.aodn.oceancurrent.service.IndexingService;
import au.org.aodn.oceancurrent.util.elasticsearch.BulkRequestProcessor;
import au.org.aodn.oceancurrent.util.elasticsearch.IndexingCallback;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

@RestController
@Profile("dev")
@RequestMapping("/indexing")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Indexing", description = "The Indexing API. Only available in development profile.")
public class IndexingController {
    private final IndexingService indexingService;
    private static final int BATCH_SIZE = 100000;

    @GetMapping
    public ResponseEntity<String> getIndexingStatus() {
        return ResponseEntity.ok("Indexing service is running");
    }

    @PostMapping
    @Operation(description = "Trigger indexing of JSON files in the data directory")
    public ResponseEntity<String> triggerIndexing() {
        log.info("Received indexing request");
        try {
            indexingService.indexRemoteJsonFiles(true,null);
            log.info("Indexing request completed");
            return ResponseEntity.ok("Indexing completed");
        } catch (IOException e) {
            log.error("Error during indexing", e);
            return ResponseEntity.internalServerError()
                    .body("Error during indexing: " + e.getMessage());
        }
    }

    @PostMapping(path = "/async")
    @Operation(description = "Index all metadata JSON files with real-time progress updates via Server-Sent Events")
    public SseEmitter indexAllMetadataAsync(
            @Parameter(description = "Flag to confirm the indexing operation")
            @RequestParam(value = "confirm", defaultValue = "false") Boolean confirm) {
        log.info("Received indexing request with async progress updates. Acknowledgement: {}", confirm);
        final SseEmitter emitter = new SseEmitter(0L); // No timeout
        final IndexingCallback callback = createCallback(emitter);

        new Thread(() -> {
            try {
                indexingService.indexRemoteJsonFiles(confirm, callback);
            } catch (IOException e) {
                emitter.completeWithError(e);
                log.error("Error during indexing", e);
            }
        }).start();
        return emitter;
    }

    @DeleteMapping
    @Operation(description = "Delete the index")
    public ResponseEntity<String> deleteIndex() {
        try {
            indexingService.deleteIndexIfExists();
            log.info("Index deletion completed");
            return ResponseEntity.ok("Index deleted");
        } catch (IOException e) {
            log.error("Error during index deletion", e);
            return ResponseEntity.internalServerError()
                    .body("Error during index deletion: " + e.getMessage());
        }
    }

    @PostMapping("/s3")
    @Operation(description = "Trigger S3 waves files indexing")
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

    @PostMapping("/s3/async")
    @Operation(description = "Index S3 waves files with real-time progress updates via Server-Sent Events")
    public SseEmitter indexS3WavesAsync() {
        log.info("Received S3 waves indexing request with async progress updates");
        final SseEmitter emitter = new SseEmitter(0L); // No timeout
        final IndexingCallback callback = createCallback(emitter);

        new Thread(() -> {
            try {
                BulkRequestProcessor processor = new BulkRequestProcessor(BATCH_SIZE, indexingService.getIndexName(), indexingService.getEsClient());
                int indexedCount = indexingService.indexS3WavesFiles(processor, callback);
                log.info("Successfully indexed {} waves files from S3", indexedCount);
            } catch (Exception e) {
                emitter.completeWithError(e);
                log.error("Error during S3 waves indexing", e);
            }
        }).start();
        return emitter;
    }

    protected IndexingCallback createCallback(SseEmitter emitter) {
        return new IndexingCallback() {
            @Override
            public void onProgress(String message) {
                try {
                    log.info("Sending progress update: {}", message);
                    SseEmitter.SseEventBuilder event = SseEmitter.event()
                            .data(message)
                            .id(String.valueOf(message.hashCode()))
                            .name("indexer_progress");

                    emitter.send(event);
                } catch (IOException e) {
                    emitter.completeWithError(e);
                }
            }

            @Override
            public void onComplete(String message) {
                try {
                    log.info("Sending completion update");
                    SseEmitter.SseEventBuilder event = SseEmitter.event()
                            .data(message)
                            .id(String.valueOf(message.hashCode()))
                            .name("indexer_complete");

                    emitter.send(event);
                    emitter.complete();
                } catch (IOException e) {
                    emitter.completeWithError(e);
                }
            }

            @Override
            public void onError(String message) {
                try {
                    log.error("Sending error update: {}", message);
                    SseEmitter.SseEventBuilder event = SseEmitter.event()
                            .data(message)
                            .id(String.valueOf(message.hashCode()))
                            .name("indexer_error");

                    emitter.send(event);
                    emitter.complete();
                } catch (IOException e) {
                    emitter.completeWithError(e);
                }
            }
        };
    }
}
