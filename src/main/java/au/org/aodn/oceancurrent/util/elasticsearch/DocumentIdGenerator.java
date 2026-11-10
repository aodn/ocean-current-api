package au.org.aodn.oceancurrent.util.elasticsearch;

import au.org.aodn.oceancurrent.model.ImageMetadataEntry;

/**
 * Utility class for generating document IDs for Elasticsearch
 */
public class DocumentIdGenerator {

    /**
     * Generates a document ID for Elasticsearch from productId, region, path and fileName.
     * Indexing the same file again updates its existing document rather than creating a duplicate.
     * <p>
     * The path is part of the ID because the same fileName can exist in several folders of one
     * product and region, and each is a different image. For example, Tidal Currents
     * {@code /tides/Bass_hv/2026/202612310000.gif} and {@code /tides/Bass_hv/2027/202612310000.gif}
     * have different content, and SealCTD tags (no region) reuse the same dated names in every tag folder.
     * Without the path, the last one indexed would overwrite the others.
     *
     * @param entry The ImageMetadataEntry to generate an ID for
     * @return The generated document ID, or null to let Elasticsearch generate one
     */
    public static String generateDocumentId(ImageMetadataEntry entry) {
        if (entry.getProductId() != null && entry.getFileName() != null) {
            String regionPart = (entry.getRegion() != null) ? entry.getRegion() : "";
            String pathPart = (entry.getPath() != null) ? entry.getPath() : "";
            return String.format("%s_%s_%s_%s", entry.getProductId(), regionPart, pathPart, entry.getFileName())
                    .replaceAll("[^a-zA-Z0-9_-]", "_")
                    .toLowerCase();
        }

        // Let Elasticsearch auto-generate it
        return null;
    }
}
