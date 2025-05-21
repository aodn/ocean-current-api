package au.org.aodn.oceancurrent.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegionLatestFileResponse {
    private String productId;
    private String region;
    private String latestFileName;
    private String path;
}
