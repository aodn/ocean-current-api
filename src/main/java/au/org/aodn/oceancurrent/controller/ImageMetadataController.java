package au.org.aodn.oceancurrent.controller;

import au.org.aodn.oceancurrent.model.ImageMetadataGroup;
import au.org.aodn.oceancurrent.service.SearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
@RequestMapping("/metadata")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Image Metadata", description = "API for searching image list metadata")
public class ImageMetadataController {
    private final SearchService searchService;

    @GetMapping("/search")
    @Operation(description = """
            Search image files metadata by `product id`, `region`, `date` and `size`. \n
            e.g. `/metadata/search?product=sixDaySst-sst&region=Au&date=20221001&size=100`
            
            Date is the center date to search around, result will be the past and future `size` "items".
            For example, if date format is day, and date is 20230701 and size is 100, the result will be from 20230323
             to 20231009 which is 100 items before and after 20230701.
            """)
    public ResponseEntity<ImageMetadataGroup> searchImageFilesMetadata(
            @Parameter(description = "Combined product id", example = "sixDaySst-sst")
            @RequestParam String productId,
            @Parameter(description = "Region name", example = "Au")
            @RequestParam String region,
            @Parameter(description = "Center date to search around", example = "20240701")
            @RequestParam String date,
            @RequestParam(defaultValue = "100") int size) {
        log.info("Received request to search files for product: {}, region: {}, date: {}, size: {}",
                productId, region, date, size);
        ImageMetadataGroup results = searchService.getImageMetadata(
                productId, region, date, size);
        return ResponseEntity.ok(results);
    }

    @GetMapping("/image-list/{productId}/{region}")
    @Operation(description = """
            Search all image files list by `product id` and `region`, \n
            e.g. `/metadata/image-list/sixDaySst-sst/Au`
            """)
    public ResponseEntity<ImageMetadataGroup> getAllImageFilesList(
            @Parameter(description = "Combined product id", example = "sixDaySst-sst")
            @PathVariable String productId,
            @Parameter(description = "Region name", example = "Au")
            @PathVariable String region
    ) {
        log.info("Received request to search files for product: {}, region: {}", productId, region);
        ImageMetadataGroup results = searchService.findAllImageList(productId, region);
        return ResponseEntity.ok(results);
    }

    @GetMapping("/product/{productId}/region/{region}")
    public ResponseEntity<ImageMetadataGroup> getFilesByProductAndRegion(
            @PathVariable String productId,
            @PathVariable String region
    ) throws IOException {
        return ResponseEntity.ok(searchService.findByProductAndRegion(productId, region));
    }

    @GetMapping("/{productId}/{region}")
    public ResponseEntity<ImageMetadataGroup> searchFiles(
            @PathVariable  String productId,
            @PathVariable  String region,
            @RequestParam(required = false) String fromDate,
            @RequestParam(required = false) String toDate,
            @RequestParam(defaultValue = "100") int size) {
        try {
            ImageMetadataGroup results = searchService.findByProductRegionAndDateRange(
                    productId,
                    region,
                    fromDate,
                    toDate,
                    size);
            return ResponseEntity.ok(results);
        } catch (IOException e) {
            return ResponseEntity.internalServerError().build();
        }
    }
}
