package au.org.aodn.oceancurrent.util.elasticsearch;

import au.org.aodn.oceancurrent.model.ImageMetadataEntry;

/**
 * Utility class for generating document IDs for Elasticsearch
 */
public class DocumentIdGenerator {

    /**
     * Generates a unique document ID for Elasticsearch based on productId, region, and fileName
     * This ensures that when indexing the same file again, it will update the existing document
     * rather than creating a duplicate
     *
     * @param entry The ImageMetadataEntry to generate an ID for
     * @return The generated document ID
     */
    public static String generateDocumentId(ImageMetadataEntry entry) {
        if (entry.getProductId() != null && entry.getFileName() != null) {
            String regionPart = (entry.getRegion() != null) ? entry.getRegion() : "";
            return String.format("%s_%s_%s", entry.getProductId(), regionPart, entry.getFileName())
                    .replaceAll("[^a-zA-Z0-9_-]", "_")
                    .toLowerCase();
        }

        // Let Elasticsearch auto-generate it
        return null;
    }
}
