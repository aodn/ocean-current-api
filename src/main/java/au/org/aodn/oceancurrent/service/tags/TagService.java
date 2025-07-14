package au.org.aodn.oceancurrent.service.tags;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class TagService {

    private final List<ProductTagService> productTagServices;
    private final Map<String, ProductTagService> servicesByProductType = new HashMap<>();

    @PostConstruct
    public void initializeServiceMappings() {
        for (ProductTagService service : productTagServices) {
            servicesByProductType.put(service.getProductType(), service);
            log.info("Registered tag service for product type: {}", service.getProductType());
        }
    }

    /**
     * Get the appropriate service for a product type
     */
    public ProductTagService getServiceForProduct(String productType) {
        ProductTagService service = servicesByProductType.get(productType);
        if (service == null) {
            throw new IllegalArgumentException("No tag service available for product type: " + productType);
        }
        return service;
    }

    /**
     * Get all supported product types
     */
    public List<String> getSupportedProductTypes() {
        return List.copyOf(servicesByProductType.keySet());
    }

    /**
     * Download data for a specific product type
     * This will force a download regardless of current data availability
     */
    public boolean downloadData(String productType) {
        try {
            log.info("Downloading data for product type: {}", productType);
            return getServiceForProduct(productType).downloadData();
        } catch (Exception e) {
            log.error("Error downloading data for product {}: {}", productType, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Ensure data is available for a specific product type
     * Will download data if needed
     */
    public boolean ensureDataAvailability(String productType) {
        try {
            log.debug("Ensuring data availability for product type: {}", productType);
            return getServiceForProduct(productType).ensureDataAvailability();
        } catch (Exception e) {
            log.error("Error ensuring data availability for product {}: {}", productType, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Check if data is available for a specific product type
     */
    public boolean isDataAvailable(String productType) {
        try {
            return getServiceForProduct(productType).isDataAvailable();
        } catch (Exception e) {
            log.debug("Error checking data availability for product {}: {}", productType, e.getMessage());
            return false;
        }
    }

    /**
     * Get all tag files for a specific product type
     */
    public List<String> getAllTagFiles(String productType) {
        try {
            log.debug("Getting all tag files for product type: {}", productType);
            return getServiceForProduct(productType).getAllTagFiles();
        } catch (Exception e) {
            log.error("Error getting tag files for product {}: {}", productType, e.getMessage(), e);
            throw new RuntimeException("Failed to get tag files for product: " + productType, e);
        }
    }

    /**
     * Check if tag file exists for a specific product type
     */
    public boolean tagFileExists(String productType, String tagFile) {
        try {
            log.debug("Checking if tag file {} exists for product type {}", tagFile, productType);
            return getServiceForProduct(productType).tagFileExists(tagFile);
        } catch (Exception e) {
            log.error("Error checking if tag file exists for product {}: {}", productType, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Check if a product has any data
     */
    public boolean hasData(String productType) {
        try {
            return getServiceForProduct(productType).hasData();
        } catch (Exception e) {
            log.debug("Error checking if product {} has data: {}", productType, e.getMessage());
            return false;
        }
    }

    /**
     * Get tags for a specific tag file from a specific product type
     */
    public Object getTagsByTagFile(String productType, String tagFile) {
        try {
            log.debug("Getting tags for tag file {} from product type {}", tagFile, productType);
            return getServiceForProduct(productType).getTagsByTagFile(tagFile);
        } catch (Exception e) {
            log.error("Error getting tags for product {} and tag file {}: {}", productType, tagFile, e.getMessage(), e);
            throw new RuntimeException("Failed to get tags for product: " + productType + ", tag file: " + tagFile, e);
        }
    }

    /**
     * Construct tag file path from date string for a specific product type
     */
    public String constructTagFilePath(String productType, String dateTime) {
        try {
            return getServiceForProduct(productType).constructTagFilePath(dateTime);
        } catch (Exception e) {
            log.error("Error constructing tag file path for product {} and date {}: {}", productType, dateTime, e.getMessage(), e);
            throw new RuntimeException("Failed to construct tag file path for product: " + productType + ", date: " + dateTime, e);
        }
    }

    /**
     * Validate date format for a specific product type
     */
    public boolean isValidDateFormat(String productType, String dateTime) {
        try {
            return getServiceForProduct(productType).isValidDateFormat(dateTime);
        } catch (Exception e) {
            log.error("Error validating date format for product {} and date {}: {}", productType, dateTime, e.getMessage(), e);
            return false;
        }
    }


}
