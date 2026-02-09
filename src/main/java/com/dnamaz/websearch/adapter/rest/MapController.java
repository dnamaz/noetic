package com.dnamaz.websearch.adapter.rest;

import com.dnamaz.websearch.service.MapService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST adapter for URL discovery via link crawling.
 */
@RestController
@RequestMapping("/api/v1")
public class MapController {

    private final MapService mapService;

    public MapController(MapService mapService) {
        this.mapService = mapService;
    }

    @PostMapping("/map")
    public MapService.MapResult map(@RequestBody Map<String, Object> body) {
        String startUrl = (String) body.get("url");
        Integer maxDepth = (Integer) body.get("maxDepth");
        Integer maxUrls = (Integer) body.get("maxUrls");
        String pathFilter = (String) body.get("pathFilter");

        return mapService.map(startUrl, maxDepth, maxUrls, pathFilter);
    }
}
