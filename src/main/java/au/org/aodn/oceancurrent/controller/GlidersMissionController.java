package au.org.aodn.oceancurrent.controller;

import au.org.aodn.oceancurrent.dto.GliderMissionPage;
import au.org.aodn.oceancurrent.model.gliders.GliderMission;
import au.org.aodn.oceancurrent.service.GlidersCatalogueService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/gliders/missions")
@RequiredArgsConstructor
@Tag(name = "Gliders Missions", description = "Browse the upstream Gliders mission catalogue")
public class GlidersMissionController {
    private final GlidersCatalogueService catalogueService;

    @GetMapping
    @Operation(summary = "Filter and paginate a source-defined mission browsing order")
    public GliderMissionPage missions(
            @RequestParam(defaultValue = "deployment_date") String order,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String nrt,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        if (nrt != null && !nrt.equalsIgnoreCase("true") && !nrt.equalsIgnoreCase("false")) {
            throw new IllegalArgumentException("nrt must be true or false");
        }
        return catalogueService.missions(order, q, nrt == null ? null : Boolean.valueOf(nrt), page, size);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a mission by its exact stable catalogue ID")
    public GliderMission mission(@PathVariable String id) {
        return catalogueService.mission(id);
    }
}
