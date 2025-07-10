package au.org.aodn.oceancurrent.controller;

import au.org.aodn.oceancurrent.service.BuoyTimeSeriesService;
import au.org.aodn.oceancurrent.service.tags.SurfaceWavesTagService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/debug")
@RequiredArgsConstructor
@Profile({"dev", "edge"})
@Slf4j
public class DebugController {

    private final BuoyTimeSeriesService buoyTimeSeriesService;
    private final SurfaceWavesTagService surfaceWavesTagService;

    @GetMapping("/sqlite-status")
    public ResponseEntity<Map<String, Object>> checkSqliteStatus() {
        Map<String, Object> status = new HashMap<>();

        try {
            boolean buoyTimeSeriesAvailable = buoyTimeSeriesService.isDatabaseAvailable();
            status.put("buoyTimeSeriesAvailable", buoyTimeSeriesAvailable);

            boolean surfaceWavesAvailable = surfaceWavesTagService.isDataAvailable();
            status.put("surfaceWavesAvailable", surfaceWavesAvailable);

            return ResponseEntity.ok(status);
        } catch (Exception e) {
            log.error("Error checking SQLite status", e);
            status.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(status);
        }
    }
}
