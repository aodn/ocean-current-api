package au.org.aodn.oceancurrent.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
@AllArgsConstructor
@NoArgsConstructor
public class ImageMetadataGroup extends ImageMetaDataBase {
    private List<FileMetadata> files;
}
