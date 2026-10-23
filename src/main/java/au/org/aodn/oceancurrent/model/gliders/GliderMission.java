package au.org.aodn.oceancurrent.model.gliders;

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
    public record Visualisations(List<Link> details, List<Link> animations) {}
    public record Link(String label, String url) {}
}
