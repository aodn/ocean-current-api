package au.org.aodn.oceancurrent.model.gliders;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record GliderMission(
        String id, String missionId, String missionName, List<String> catalogueNumbers,
        String processingVersion, boolean isNrt, String datesRaw, String startDate, String endDate,
        Double latitude, Double longitude, Double durationDays, Double distanceTravelledKm,
        String gliderId, Visualisations visualisations, String temperatureSalinityUrl,
        String scientificDataUrl) {
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Visualisations(List<Link> details, List<Link> animations, LatestImages latestImages) {
        public Visualisations {
            if (latestImages == null) latestImages = new LatestImages(null, null, null);
        }
    }
    public record LatestImages(@JsonProperty("4d") String fourDay,
                               @JsonProperty("12d") String twelveDay,
                               @JsonProperty("30d") String thirtyDay) {}
    public record Link(String label, String url) {}
}
