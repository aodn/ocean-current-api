package au.org.aodn.oceancurrent.service;

import java.util.List;

/**
 * Interface for product-specific tag services.
 * Each product type (e.g., surface waves, ocean color, etc.) will have its own implementation.
 */
public interface ProductTagService {

    /**
     * Get the product type this service handles
     */
    String getProductType();

    /**
     * Download/update the data source for this product
     */
    boolean downloadData();

    /**
     * Check if the data source is available for this product
     */
    boolean isDataAvailable();

    /**
     * Check if the data source has any data
     */
    boolean hasData();

    /**
     * Get all available tag files for this product
     */
    List<String> getAllTagFiles();

    /**
     * Check if a specific tag file exists for this product
     */
    boolean tagFileExists(String tagFile);

    /**
     * Get tags for a specific tag file
     * Returns a generic object that can be cast to the appropriate response type
     */
    Object getTagsByTagFile(String tagFile);

    /**
     * Construct tag file path from date string (product-specific logic)
     */
    String constructTagFilePath(String dateTime);

    /**
     * Validate date format for this product
     */
    boolean isValidDateFormat(String dateTime);
}
