package au.org.aodn.oceancurrent.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WaveTagResponse {

    private String tagFile;
    private List<TagData> tags; // Ordered by the 'order' field

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TagData {
        private Integer x;
        private Integer y;
        private Integer sz;
        private String title;
        private String url;
    }
}
