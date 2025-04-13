package au.org.aodn.oceancurrent.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
public class CurrentMetersPlotResponse {
    private String productId;
    private String region;
    private List<DepthData> depthData = new ArrayList<>();

    @Data
    @NoArgsConstructor
    public static class DepthData {
        private String depth;
        private String path;
        private List<String> files = new ArrayList<>();
    }
}
