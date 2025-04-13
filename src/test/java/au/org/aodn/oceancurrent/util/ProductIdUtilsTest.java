package au.org.aodn.oceancurrent.util;

import au.org.aodn.oceancurrent.model.ImageMetadataEntry;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ProductIdUtilsTest {

    @Test
    public void testFindHighestVersionNumber_WithValidEntries() {
        // Arrange
        List<ImageMetadataEntry> entries = Arrays.asList(
                createEntry("currentMetersPlot-48", "BMP120", "xyz"),
                createEntry("currentMetersPlot-49", "BMP120", "zt"),
                createEntry("currentMetersPlot-52", "BMP120", "xyz"),
                createEntry("currentMetersPlot-50", "BMP120", "zt"),
                createEntry("currentMetersPlot-51", "BMP120", "xyz")
        );

        // Act
        Integer result = ProductIdUtils.findHighestVersionNumber(entries, "currentMetersPlot");

        // Assert
        assertEquals(52, result, "Should find the highest version number");
    }

    @Test
    public void testFindHighestVersionNumber_WithSingleEntry() {
        // Arrange
        List<ImageMetadataEntry> entries = Collections.singletonList(
                createEntry("currentMetersPlot-48", "BMP120", "xyz")
        );

        // Act
        Integer result = ProductIdUtils.findHighestVersionNumber(entries, "currentMetersPlot");

        // Assert
        assertEquals(48, result, "Should return the only version number available");
    }

    @Test
    public void testFindHighestVersionNumber_WithNonSequentialNumbers() {
        // Arrange
        List<ImageMetadataEntry> entries = Arrays.asList(
                createEntry("currentMetersPlot-3", "BMP120", "xyz"),
                createEntry("currentMetersPlot-99", "BMP120", "zt"),
                createEntry("currentMetersPlot-42", "BMP120", "xyz"),
                createEntry("currentMetersPlot-7", "BMP120", "zt")
        );

        // Act
        Integer result = ProductIdUtils.findHighestVersionNumber(entries, "currentMetersPlot");

        // Assert
        assertEquals(99, result, "Should find the highest version number regardless of sequence");
    }

    @Test
    public void testFindHighestVersionNumber_WithMixedEntries() {
        // Arrange
        List<ImageMetadataEntry> entries = Arrays.asList(
                createEntry("currentMetersPlot-48", "BMP120", "xyz"),
                createEntry("otherProduct-123", "BMP120", "zt"),
                createEntry("currentMetersPlot-52", "BMP120", "xyz"),
                createEntry("randomData", "BMP120", "zt")
        );

        // Act
        Integer result = ProductIdUtils.findHighestVersionNumber(entries, "currentMetersPlot");

        // Assert
        assertEquals(52, result, "Should find the highest version among only matching product IDs");
    }

    @Test
    public void testFindHighestVersionNumber_WithEmptyList() {
        // Arrange
        List<ImageMetadataEntry> entries = Collections.emptyList();

        // Act
        Integer result = ProductIdUtils.findHighestVersionNumber(entries, "currentMetersPlot");

        // Assert
        assertNull(result, "Should return null for empty list of entries");
    }

    @Test
    public void testFindHighestVersionNumber_WithNullInputs() {
        // Arrange & Act
        // Test with null entries
        assertNull(ProductIdUtils.findHighestVersionNumber(null, "currentMetersPlot"),
                "Should return null for null entries");

        // Arrange
        List<ImageMetadataEntry> entries = Collections.singletonList(
                createEntry("currentMetersPlot-48", "BMP120", "xyz")
        );

        // Act & Assert
        assertNull(ProductIdUtils.findHighestVersionNumber(entries, null),
                "Should return null for null prefix");
    }

    @Test
    public void testFindEntriesWithHighestVersion_MultipleEntriesSameVersion() {
        // Arrange
        List<ImageMetadataEntry> entries = Arrays.asList(
                createEntry("currentMetersPlot-48", "BMP120", "xyz"),
                createEntry("currentMetersPlot-48", "BMP120", "abc"), // Different depth
                createEntry("currentMetersPlot-48", "BMP121", "xyz"), // Different region
                createEntry("currentMetersPlot-47", "BMP120", "zt")
        );

        // Act
        List<ImageMetadataEntry> result = ProductIdUtils.findEntriesWithHighestVersion(entries, "currentMetersPlot");

        // Assert
        assertEquals(3, result.size(), "Should return all entries with the highest version");
        assertTrue(result.stream().allMatch(e -> "currentMetersPlot-48".equals(e.getProductId())),
                "All returned entries should have the highest version product ID");
    }

    @Test
    public void testFindEntriesWithHighestVersion_NoMatchingEntries() {
        // Arrange
        List<ImageMetadataEntry> entries = Arrays.asList(
                createEntry("otherProduct-123", "BMP120", "zt"),
                createEntry("randomData", "BMP120", "zt")
        );

        // Act
        List<ImageMetadataEntry> result = ProductIdUtils.findEntriesWithHighestVersion(entries, "currentMetersPlot");

        // Assert
        assertTrue(result.isEmpty(), "Should return empty list when no entries match the pattern");
    }

    @Test
    public void testFindHighestVersionCurrentMetersPlotEntries() {
        // Arrange
        List<ImageMetadataEntry> entries = Arrays.asList(
                createEntry("currentMetersPlot-49", "BMP120", "xyz"),
                createEntry("currentMetersPlot-49", "BMP120", "zt"),
                createEntry("currentMetersPlot-48", "BMP121", "xyz")
        );

        // Act
        List<ImageMetadataEntry> result = ProductIdUtils.findHighestVersionCurrentMetersPlotEntries(entries);

        // Assert
        assertEquals(2, result.size(), "Convenience method should find all entries with highest version");
        assertTrue(result.stream().allMatch(e -> "currentMetersPlot-49".equals(e.getProductId())),
                "All returned entries should have the highest version product ID");
    }

    @Test
    public void testExtractVersionNumber_WithValidInput() {
        // Arrange & Act
        Integer result = ProductIdUtils.extractVersionNumber("currentMetersPlot-48");

        // Assert
        assertEquals(48, result, "Should extract correct version number");
    }

    @Test
    public void testExtractVersionNumber_WithInvalidInputs() {
        // Arrange & Act & Assert
        // Test null input
        assertNull(ProductIdUtils.extractVersionNumber(null), "Should return null for null input");

        // Test invalid format
        assertNull(ProductIdUtils.extractVersionNumber("currentMetersPlot-"),
                "Should return null for invalid format");

        assertNull(ProductIdUtils.extractVersionNumber("currentMetersPlot-abc"),
                "Should return null for non-numeric version");

        assertNull(ProductIdUtils.extractVersionNumber("otherProduct-48"),
                "Should return null for different product prefix");
    }

    /**
     * Helper method to create test ImageMetadataEntry objects
     */
    private ImageMetadataEntry createEntry(String productId, String region, String depth) {
        ImageMetadataEntry entry = new ImageMetadataEntry();
        entry.setProductId(productId);
        entry.setRegion(region);
        entry.setDepth(depth);
        entry.setPath("/timeseries/ANMN_P" + productId.replaceAll("[^0-9]", "") + "/" + region + "/" + depth);
        entry.setFileName(region + "-" + Math.abs(productId.hashCode() % 10000) +
                "-Sentinel-or-Monitor-Workhorse-ADCP-" +
                (100 + Math.abs(depth.hashCode() % 20)) + "p9_" + depth + ".gif");
        return entry;
    }
}
