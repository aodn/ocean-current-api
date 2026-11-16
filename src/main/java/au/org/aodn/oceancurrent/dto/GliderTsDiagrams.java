package au.org.aodn.oceancurrent.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;

/** Complete legacy T-S index in source order, including diagrams without catalogue missions. */
public record GliderTsDiagrams(List<Diagram> diagrams) {
    /** {@code imageUrl} is a website-root-relative GIF path, null when unavailable. */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Diagram(String id, String imageUrl, List<String> missionIds) {}
}
