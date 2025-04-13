package au.org.aodn.oceancurrent.controller;

import au.org.aodn.oceancurrent.dto.CurrentMetersPlotResponse;
import au.org.aodn.oceancurrent.exception.InvalidProductException;
import au.org.aodn.oceancurrent.model.ImageMetadataGroup;
import au.org.aodn.oceancurrent.service.ProductService;
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
import java.util.List;

@RestController
@RequestMapping("/metadata")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Image Metadata", description = "API for searching image list metadata")
public class ImageMetadataController {
    private final SearchService searchService;
    private final ProductService productService;

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

    @GetMapping("/image-list/current-meters/{plotName}")
    @Operation(description = """
            Get current meters image files list by `plot name` \n
            e.g. `/metadata/image-list/current-meters/BMP120` \n
            It only returns the latest plot data if there are multiple versions of the same plot name. \n
            """)
    public ResponseEntity<CurrentMetersPlotResponse> getCurrentMetersImageFilesList(
            @Parameter(description = "Plot name", example = "BMP120")
            @PathVariable(required = false) String plotName
    ) {
        log.info("Received request to search current meters image files for plot: {},", plotName);

        CurrentMetersPlotResponse results = searchService.findLatestCurrentMetersPlotByPlotName(plotName);
        return ResponseEntity.ok(results);
    }

    @GetMapping("/image-list/{productId}")
    @Operation(description = """
            Search all image files list by `product id` and filter by `region`, `depth` \n
            e.g. `/metadata/image-list/sixDaySst-sst?region=Au`,
            `/metadata/image-list/currentMetersPlot-48?region=SAM4CY&depth=xyz`. \n
            Valid product id list can be found in `/products/leaf` endpoint \n
            """)
    public ResponseEntity<List<ImageMetadataGroup>> getAllImageFilesList(
            @Parameter(description = "Combined product id", example = "sixDaySst-sst")
            @PathVariable String productId,
            @Parameter(description = "Region name", example = "Au")
            @RequestParam(required = false) String region,
            @Parameter(description = "Depth name. For example, use 'xyz' when search for`Current Meters Plot`")
            @RequestParam(required = false) String depth
    ) {
        log.info("Received request to search files for product: {}, region: {}, depth: {}", productId, region, depth);
        if (!productService.isValidProductId(productId)) {
            throw new InvalidProductException("Invalid product ID: " + productId);
        }
        if (productService.isRegionRequired(productId) && (region == null || region.isEmpty())) {
            throw new InvalidProductException("Region is required for product ID: " + productId);
        }
        if (!productService.isRegionRequired(productId) && (region != null && !region.isEmpty())) {
            throw new InvalidProductException(
                    "This product does not have a Region parameter. Please remove it from product ID: "
                            + productId);
        }
        if (!productService.isDepthRequired(productId) && (depth != null && !depth.isBlank())) {
            throw new InvalidProductException(
                    "This product does not have a Depth parameter. Please remove it from product ID: "
                            + productId);
        }
        List<ImageMetadataGroup> results = searchService.findAllImageList(productId, region, depth);
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
