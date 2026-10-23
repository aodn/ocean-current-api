package au.org.aodn.oceancurrent.dto;

import au.org.aodn.oceancurrent.model.gliders.GliderMission;
import java.util.List;

public record GliderMissionPage(List<GliderMission> missions, int total, int page, int size, String order) {}
