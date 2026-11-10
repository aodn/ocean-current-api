package au.org.aodn.oceancurrent.util.elasticsearch;

import au.org.aodn.oceancurrent.model.ImageMetadataEntry;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Utility class for generating document IDs for Elasticsearch
 */
public class DocumentIdGenerator {

    // 16 bytes (32 hex chars) of SHA-256: collisions are negligible even across millions of documents
    private static final int HASH_BYTES = 16;

    /**
     * Generates a document ID for Elasticsearch from productId, region, path and fileName.
     * Indexing the same file again updates its existing document rather than creating a duplicate.
     * <p>
     * The path is part of the ID because the same fileName can exist in several folders of one
     * product and region, and each is a different image. For example, Tidal Currents
     * {@code /tides/Bass_hv/2026/202612310000.gif} and {@code /tides/Bass_hv/2027/202612310000.gif}
     * have different content, and SealCTD tags (no region) reuse the same dated names in every tag folder.
     * <p>
     * The ID is a readable, sanitised prefix plus a hash of the raw components. Sanitising alone is not
     * one-to-one ({@code /a/b} and {@code /a_b}, or names differing only by case, sanitise to the same
     * text), so the hash keeps such files apart.
     *
     * @param entry The ImageMetadataEntry to generate an ID for
     * @return The generated document ID, or null to let Elasticsearch generate one
     */
    public static String generateDocumentId(ImageMetadataEntry entry) {
        if (entry.getProductId() != null && entry.getFileName() != null) {
            String regionPart = (entry.getRegion() != null) ? entry.getRegion() : "";
            String pathPart = (entry.getPath() != null) ? entry.getPath() : "";
            String readablePrefix = String.format("%s_%s_%s_%s",
                            entry.getProductId(), regionPart, pathPart, entry.getFileName())
                    .replaceAll("[^a-zA-Z0-9_-]", "_")
                    .toLowerCase();
            String hash = hashComponents(entry.getProductId(), entry.getRegion(), entry.getPath(), entry.getFileName());
            return readablePrefix + "_" + hash;
        }

        // Let Elasticsearch auto-generate it
        return null;
    }

    /**
     * Hashes the raw components with an unambiguous encoding: each one is written as its length
     * (or -1 for null) followed by ':' and its value, so no two different inputs give the same bytes.
     */
    private static String hashComponents(String... components) {
        StringBuilder encoded = new StringBuilder();
        for (String component : components) {
            if (component == null) {
                encoded.append("-1:");
            } else {
                encoded.append(component.length()).append(':').append(component);
            }
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(encoded.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, HASH_BYTES);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is required on every Java platform
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
