package au.org.aodn.oceancurrent.dto;

import au.org.aodn.oceancurrent.model.gliders.GliderMission;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

import java.util.List;
import java.util.Map;

public record GliderMissionDetail(
        @JsonUnwrapped GliderMission mission,
        @JsonProperty("image_history") Map<String, List<Frame>> imageHistory) {
    /** Local product date without a timezone; URL is relative to the resource server root. */
    public record Frame(String date, String url) {}
}
