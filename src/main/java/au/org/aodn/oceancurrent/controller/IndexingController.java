package au.org.aodn.oceancurrent.controller;

import au.org.aodn.oceancurrent.service.IndexingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
            indexingService.indexJsonFiles();
            log.info("Indexing completed");
            return ResponseEntity.ok("Indexing completed");
        } catch (IOException e) {
            log.error("Error during indexing", e);
            return ResponseEntity.internalServerError()
                    .body("Error during indexing: " + e.getMessage());
        }
    }

    @DeleteMapping
    @Operation(description = "Delete the index")
    public ResponseEntity<String> deleteIndex() {
        try {
            indexingService.deleteIndexIfExists();
            log.info("Index deleted");
            return ResponseEntity.ok("Index deleted");
        } catch (IOException e) {
            log.error("Error during index deletion", e);
            return ResponseEntity.internalServerError()
                    .body("Error during index deletion: " + e.getMessage());
        }
    }
}
