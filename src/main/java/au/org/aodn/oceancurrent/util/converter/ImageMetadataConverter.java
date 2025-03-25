package au.org.aodn.oceancurrent.util.converter;

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

    public static FileMetadata toFileMetadata(ImageMetadataEntry flat) {
        FileMetadata FileMetadata = new FileMetadata();
        FileMetadata.setName(flat.getFileName());
        return FileMetadata;
    }
}
