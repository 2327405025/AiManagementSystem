package com.example.taskmanager.controller;

import com.example.taskmanager.dto.VectorDtos.SemanticSearchResponse;
import com.example.taskmanager.service.SemanticSearchService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tasks")
public class SearchController {
    private final SemanticSearchService service;

    public SearchController(SemanticSearchService service) {
        this.service = service;
    }

    @GetMapping("/semantic-search")
    SemanticSearchResponse search(
            @RequestParam String query,
            @RequestParam(defaultValue = "10") int limit) {
        if (query.isBlank() || query.length() > 500) {
            throw new IllegalArgumentException("query must contain 1 to 500 characters");
        }
        if (limit < 1 || limit > 50) {
            throw new IllegalArgumentException("limit must be between 1 and 50");
        }
        return service.search(query.trim(), limit);
    }
}
