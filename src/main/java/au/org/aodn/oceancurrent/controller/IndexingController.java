package au.org.aodn.oceancurrent.controller;

import au.org.aodn.oceancurrent.service.IndexingService;
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

@RestController
@Profile({"dev", "edge"})
@RequestMapping("/indexing")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Indexing", description = "The Indexing API. Only available in development and edge environment.")
public class IndexingController {
    private final IndexingService indexingService;

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
            } catch (Exception e) {
                log.error("Error during indexing", e);
                emitter.completeWithError(e);
                log.error("Error during indexing", e);
            }
        }).start();
        return emitter;
    }

    @PostMapping(path = "/s3")
    @Operation(description = "Trigger indexing of S3 files")
    public ResponseEntity<String> triggerS3Indexing() {
        log.info("Received S3 indexing request");
        try {
            indexingService.indexS3SurfaceWavesFiles(true);
            log.info("S3 indexing request completed");
            return ResponseEntity.ok("S3 indexing completed");
        } catch (IOException e) {
            log.error("Error during S3 indexing", e);
            return ResponseEntity.internalServerError()
                    .body("Error during S3 indexing: " + e.getMessage());
        }
    }

    @PostMapping(path = "/s3/async")
    @Operation(description = "Index all S3 files with real-time progress updates via Server-Sent Events")
    public SseEmitter indexS3FilesAsync(
            @Parameter(description = "Flag to confirm the S3 indexing operation")
            @RequestParam(value = "confirm", defaultValue = "false") Boolean confirm) {
        log.info("Received S3 indexing request with async progress updates. Acknowledgement: {}", confirm);
        final SseEmitter emitter = new SseEmitter(0L); // No timeout
        final IndexingCallback callback = createCallback(emitter);

        new Thread(() -> {
            try {
                indexingService.indexS3SurfaceWavesFiles(confirm, callback);
            } catch (IOException e) {
                emitter.completeWithError(e);
                log.error("Error during S3 indexing", e);
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
