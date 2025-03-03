package au.org.aodn.oceancurrent.util.converter;

import au.org.aodn.oceancurrent.model.FileMetadata;
import au.org.aodn.oceancurrent.model.ImageMetadataEntry;
import au.org.aodn.oceancurrent.model.ImageMetadataGroup;
import lombok.experimental.UtilityClass;

import java.util.List;
import java.util.stream.Collectors;

@UtilityClass
public class ImageMetadataConverter {
    public static ImageMetadataGroup toGroup(List<ImageMetadataEntry> flatResults) {
        if (flatResults == null || flatResults.isEmpty()) {
            return null;
        }

        ImageMetadataEntry firstResult = flatResults.get(0);

        ImageMetadataGroup metadataGroup = new ImageMetadataGroup();
        metadataGroup.setPath(firstResult.getPath());
        metadataGroup.setProductId(firstResult.getProductId());
        metadataGroup.setRegion(firstResult.getRegion());

        List<FileMetadata> files = flatResults.stream()
                .map(ImageMetadataConverter::toFileMetadata)
                .collect(Collectors.toList());

        metadataGroup.setFiles(files);
        return metadataGroup;
    }

    private static FileMetadata toFileMetadata(ImageMetadataEntry flat) {
        FileMetadata FileMetadata = new FileMetadata();
        FileMetadata.setName(flat.getFileName());
        FileMetadata.setPath(flat.getFilePath());
        return FileMetadata;
    }
}
