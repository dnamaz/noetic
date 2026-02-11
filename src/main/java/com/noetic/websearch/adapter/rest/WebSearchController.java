package com.noetic.websearch.adapter.rest;

import com.noetic.websearch.model.SearchRequest;
import com.noetic.websearch.model.SearchResponse;
import com.noetic.websearch.service.NamespaceResolver;
import com.noetic.websearch.service.WebSearchService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

/**
 * REST adapter for web search operations.
 */
@RestController
@RequestMapping("/api/v1")
public class WebSearchController {

    private final WebSearchService webSearchService;
    private final NamespaceResolver namespaceResolver;

    public WebSearchController(WebSearchService webSearchService,
                                NamespaceResolver namespaceResolver) {
        this.webSearchService = webSearchService;
        this.namespaceResolver = namespaceResolver;
    }

    @PostMapping("/search")
    public SearchResponse search(@RequestBody SearchRequest request,
                                  @RequestParam(required = false) String namespace,
                                  HttpServletRequest httpRequest) {
        String ns = namespaceResolver.resolve(namespace, httpRequest);
        return webSearchService.search(request, ns);
    }
}
