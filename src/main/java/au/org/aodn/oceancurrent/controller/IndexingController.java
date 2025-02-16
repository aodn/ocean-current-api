package au.org.aodn.oceancurrent.controller;

import au.org.aodn.oceancurrent.service.IndexingService;
import au.org.aodn.oceancurrent.util.elasticsearch.IndexingCallback;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

@RestController
@Profile("dev")
@RequestMapping("/indexing")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Indexing", description = "The Indexing API. Only available in development profile.")
public class IndexingController {
    private final IndexingService indexingService;

    @GetMapping
    public ResponseEntity<String> getIndexingStatus() {
        return ResponseEntity.ok("Indexing service is running");
    }

    @PostMapping
    @Operation(description = "Trigger indexing of JSON files in the data directory")
    public ResponseEntity<String> triggerIndexing() {
        try {
            log.info("Received indexing request");
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
    @Operation(description = "Index all metadata records with real-time progress updates via SSE")
    public SseEmitter indexAllMetadataAsync(
            @RequestParam(value = "confirm", defaultValue = "false") Boolean confirm) {
        final SseEmitter emitter = new SseEmitter(0L); // No timeout
        final IndexingCallback callback = createCallback(emitter);

        new Thread(() -> {
            try {
                indexingService.indexRemoteJsonFiles(confirm, callback);
            } catch (IOException e) {
                emitter.completeWithError(e);
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
