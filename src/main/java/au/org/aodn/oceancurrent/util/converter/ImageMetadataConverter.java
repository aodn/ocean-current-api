package au.org.aodn.oceancurrent.util.converter;

import au.org.aodn.oceancurrent.dto.CurrentMetersPlotResponse;
import au.org.aodn.oceancurrent.model.FileMetadata;
import au.org.aodn.oceancurrent.model.ImageMetadataEntry;
import au.org.aodn.oceancurrent.model.ImageMetadataGroup;
import lombok.experimental.UtilityClass;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@UtilityClass
public class ImageMetadataConverter {
    public static ImageMetadataGroup toMetadataGroup(List<ImageMetadataEntry> entries, String productId, String region) {
        ImageMetadataGroup metadataGroup = new ImageMetadataGroup();

        metadataGroup.setProductId(productId);
        metadataGroup.setRegion(region);

        if (entries != null && !entries.isEmpty()) {
            ImageMetadataEntry firstResult = entries.get(0);
            metadataGroup.setPath(firstResult.getPath());

            List<FileMetadata> files = entries.stream()
                    .map(ImageMetadataConverter::toFileMetadata)
                    .sorted(Comparator.comparing(FileMetadata::getName))
                    .collect(Collectors.toList());

            metadataGroup.setFiles(files);
        }

        return metadataGroup;
    }

    public static List<ImageMetadataGroup> createMetadataGroups(List<ImageMetadataEntry> entries) {
        if (entries.isEmpty()) {
            return Collections.emptyList();
        }

        // Group entries by depth value (preserving null)
        Map<String, List<ImageMetadataEntry>> entriesByDepth =
                entries.stream().collect(Collectors.groupingBy(entry -> {
                    String depth = entry.getDepth();
                    // Use the string "null" as the map key for null values
                    return depth == null ? "null" : depth;
                }));

        List<ImageMetadataGroup> groups = new ArrayList<>();

        for (Map.Entry<String, List<ImageMetadataEntry>> depthGroup : entriesByDepth.entrySet()) {
            String depthKey = depthGroup.getKey();
            List<ImageMetadataEntry> depthEntries = depthGroup.getValue();

            if (!depthEntries.isEmpty()) {
                ImageMetadataGroup group = new ImageMetadataGroup();
                ImageMetadataEntry firstEntry = depthEntries.get(0);

                group.setPath(firstEntry.getPath());
                group.setProductId(firstEntry.getProductId());
                group.setRegion(firstEntry.getRegion());

                // Convert "null" string back to actual null
                group.setDepth(depthKey.equals("null") ? null : depthKey);

                List<FileMetadata> files = depthEntries.stream()
                        .map(ImageMetadataConverter::toFileMetadata)
                        .sorted(Comparator.comparing(FileMetadata::getName))
                        .collect(Collectors.toList());

                group.setFiles(files);
                groups.add(group);
            }
        }

        return groups;
    }

    /**
     * Creates a CurrentMetersPlotResponse from image metadata entries
     * Since we're querying by productId and region, there will only be one productId and region
     * with potentially multiple depths
     */
    public static CurrentMetersPlotResponse createCurrentMetersPlotResponse(List<ImageMetadataEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return null;
        }

        // All entries should have the same productId and region since we're querying by these
        ImageMetadataEntry firstEntry = entries.get(0);
        String productId = firstEntry.getProductId();
        String region = firstEntry.getRegion();

        // Group entries by depth
        Map<String, List<ImageMetadataEntry>> entriesByDepth = entries.stream()
                .collect(Collectors.groupingBy(entry ->
                        entry.getDepth() != null ? entry.getDepth() : "null"
                ));

        CurrentMetersPlotResponse response = new CurrentMetersPlotResponse();
        response.setProductId(productId);
        response.setRegion(region);

        List<CurrentMetersPlotResponse.DepthData> depthDataList = new ArrayList<>();

        for (Map.Entry<String, List<ImageMetadataEntry>> depthGroup : entriesByDepth.entrySet()) {
            String depthKey = depthGroup.getKey();
            List<ImageMetadataEntry> depthEntries = depthGroup.getValue();

            if (!depthEntries.isEmpty()) {
                CurrentMetersPlotResponse.DepthData depthData = new CurrentMetersPlotResponse.DepthData();

                // Convert "null" string back to actual null
                depthData.setDepth(depthKey.equals("null") ? null : depthKey);
                depthData.setPath(depthEntries.get(0).getPath());

                // Extract just the filenames and sort them
                List<String> fileNames = depthEntries.stream()
                        .map(ImageMetadataEntry::getFileName)
                        .sorted()
                        .collect(Collectors.toList());

                depthData.setFiles(fileNames);
                depthDataList.add(depthData);
            }
        }

        response.setDepthData(depthDataList);

        return response;
    }


    /**
     * Groups image metadata entries by productId, region, and depth.
     * This method is specifically for the currentMetersPlot endpoint.
     *
     * @param entries The list of image metadata entries to group
     * @return A list of grouped metadata
     */
    public static List<ImageMetadataGroup> createMetadataGroupsByProductRegionDepth(List<ImageMetadataEntry> entries) {
        if (entries.isEmpty()) {
            return Collections.emptyList();
        }

        // Group by productId -> region -> depth
        Map<String, Map<String, Map<String, List<ImageMetadataEntry>>>> groupedEntries =
                entries.stream()
                        .collect(Collectors.groupingBy(
                                ImageMetadataEntry::getProductId,
                                Collectors.groupingBy(
                                        ImageMetadataEntry::getRegion,
                                        Collectors.groupingBy(entry -> {
                                            String depth = entry.getDepth();
                                            // Use the string "null" as the map key for null values
                                            return depth == null ? "null" : depth;
                                        })
                                )
                        ));

        List<ImageMetadataGroup> groups = new ArrayList<>();

        // Process the nested maps to create ImageMetadataGroup instances
        for (Map.Entry<String, Map<String, Map<String, List<ImageMetadataEntry>>>> productEntry : groupedEntries.entrySet()) {
            String productId = productEntry.getKey();

            for (Map.Entry<String, Map<String, List<ImageMetadataEntry>>> regionEntry : productEntry.getValue().entrySet()) {
                String region = regionEntry.getKey();

                for (Map.Entry<String, List<ImageMetadataEntry>> depthEntry : regionEntry.getValue().entrySet()) {
                    String depthKey = depthEntry.getKey();
                    List<ImageMetadataEntry> depthEntries = depthEntry.getValue();

                    if (!depthEntries.isEmpty()) {
                        ImageMetadataGroup group = new ImageMetadataGroup();
                        ImageMetadataEntry firstEntry = depthEntries.get(0);

                        group.setPath(firstEntry.getPath());
                        group.setProductId(productId);
                        group.setRegion(region);

                        // Convert "null" string back to actual null
                        group.setDepth(depthKey.equals("null") ? null : depthKey);

                        List<FileMetadata> files = depthEntries.stream()
                                .map(ImageMetadataConverter::toFileMetadata)
                                .sorted(Comparator.comparing(FileMetadata::getName))
                                .collect(Collectors.toList());

                        group.setFiles(files);
                        groups.add(group);
                    }
                }
            }
        }

        return groups;
    }

    public static FileMetadata toFileMetadata(ImageMetadataEntry flat) {
        FileMetadata FileMetadata = new FileMetadata();
        FileMetadata.setName(flat.getFileName());
        return FileMetadata;
    }
}
