package au.org.aodn.oceancurrent.controller;

import au.org.aodn.oceancurrent.model.ImageMetadataGroup;
import au.org.aodn.oceancurrent.service.SearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

@RestController
@RequestMapping("/metadata")
@RequiredArgsConstructor
public class ImageMetadataController {
    private final SearchService searchService;

    @GetMapping("/product/{product}/region/{region}")
    public ResponseEntity<ImageMetadataGroup> getFilesByProductAndRegion(
            @PathVariable String product,
            @PathVariable String region
    ) throws IOException {
        return ResponseEntity.ok(searchService.findByProductAndRegion(product, region));
    }

    @GetMapping("/{product}/{subProduct}/{region}")
    public ResponseEntity<ImageMetadataGroup> searchFiles(
            @PathVariable  String product,
            @PathVariable  String subProduct,
            @PathVariable  String region,
            @RequestParam(required = false) String fromDate,
            @RequestParam(required = false) String toDate,
            @RequestParam(defaultValue = "100") int size) {
        try {
            ImageMetadataGroup results = searchService.findByProductRegionAndDateRange(
                    product,
                    subProduct,
                    region,
                    fromDate,
                    toDate,
                    size);
            return ResponseEntity.ok(results);
        } catch (IOException e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/search")
    public ResponseEntity<ImageMetadataGroup> searchFilesByDate(
            @RequestParam String product,
            @RequestParam String subProduct,
            @RequestParam String region,
            @RequestParam String date,
            @RequestParam(defaultValue = "100") int size) {
        ImageMetadataGroup results = searchService.searchFilesAroundDate(
                product, subProduct, region, date, size);
        return ResponseEntity.ok(results);
    }
}
