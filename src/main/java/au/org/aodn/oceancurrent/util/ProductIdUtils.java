package au.org.aodn.oceancurrent.util;

import au.org.aodn.oceancurrent.model.ImageMetadataEntry;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class ProductIdUtils {
    private static final Pattern CURRENT_METERS_PLOT_PATTERN = Pattern.compile("currentMetersPlot-(\\d+)");

    private ProductIdUtils() {
        // Utility class, no instantiation
    }
    /**
     * Finds the highest version number from a list of metadata entries
     *
     * @param entries List of metadata entries
     * @param prefix The prefix to filter by (e.g., "currentMetersPlot")
     * @return The highest version number, or null if none found
     */
    public static Integer findHighestVersionNumber(List<ImageMetadataEntry> entries, String prefix) {
        if (entries == null || entries.isEmpty() || prefix == null || prefix.trim().isEmpty()) {
            return null;
        }

        Pattern pattern = Pattern.compile(Pattern.quote(prefix) + "-(\\d+)");

        // Extract version numbers and find the highest one
        return entries.stream()
                .map(ImageMetadataEntry::getProductId)
                .filter(id -> id != null && id.startsWith(prefix + "-"))
                .map(id -> {
                    Matcher matcher = pattern.matcher(id);
                    if (matcher.matches()) {
                        return Integer.parseInt(matcher.group(1));
                    }
                    return null;
                })
                .filter(Objects::nonNull)
                .max(Integer::compare)
                .orElse(null);
    }

    /**
     * Finds all entries that have the highest version number
     *
     * @param entries List of metadata entries
     * @param prefix The prefix to filter by (e.g., "currentMetersPlot")
     * @return List of entries with the highest version number, or empty list if none found
     */
    public static List<ImageMetadataEntry> findEntriesWithHighestVersion(List<ImageMetadataEntry> entries, String prefix) {
        if (entries == null || entries.isEmpty() || prefix == null || prefix.trim().isEmpty()) {
            return Collections.emptyList();
        }

        // Find the highest version number
        Integer highestVersion = findHighestVersionNumber(entries, prefix);

        if (highestVersion == null) {
            return Collections.emptyList();
        }

        // Create the product ID pattern with the highest version
        String highestVersionProductId = prefix + "-" + highestVersion;

        // Return all entries with this highest version product ID
        return entries.stream()
                .filter(entry -> highestVersionProductId.equals(entry.getProductId()))
                .collect(Collectors.toList());
    }

    /**
     * Finds all entries with the highest version number specifically for current meters plot
     *
     * @param entries List of metadata entries
     * @return List of entries with the highest version number, or empty list if none found
     */
    public static List<ImageMetadataEntry> findHighestVersionCurrentMetersPlotEntries(List<ImageMetadataEntry> entries) {
        return findEntriesWithHighestVersion(entries, "currentMetersPlot");
    }

    /**
     * Extracts the version number from a product ID
     *
     * @param productId The product ID (e.g., "currentMetersPlot-48")
     * @return The version number as an Integer, or null if not found
     */
    public static Integer extractVersionNumber(String productId) {
        if (productId == null) {
            return null;
        }

        Matcher matcher = CURRENT_METERS_PLOT_PATTERN.matcher(productId);
        if (matcher.matches()) {
            return Integer.parseInt(matcher.group(1));
        }
        return null;
    }
}
