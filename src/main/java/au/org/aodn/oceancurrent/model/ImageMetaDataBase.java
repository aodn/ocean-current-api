package au.org.aodn.oceancurrent.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ImageMetaDataBase {
    private String path;
    private String productId;
    private String region;
    private String depth;
}
