package com.example.taskmanager.controller;

import com.example.taskmanager.ai.AiService;
import com.example.taskmanager.dto.AiDtos.DecomposeRequest;
import com.example.taskmanager.dto.AiDtos.Decomposition;
import com.example.taskmanager.dto.AiDtos.NaturalLanguageRequest;
import com.example.taskmanager.dto.AiDtos.TaskSuggestion;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai")
public class AiController {
    private final AiService service;

    public AiController(AiService service) {
        this.service = service;
    }

    @PostMapping("/parse-task")
    TaskSuggestion parseTask(@Valid @RequestBody NaturalLanguageRequest request) {
        return service.parse(request.text());
    }

    @PostMapping("/decompose")
    Decomposition decompose(@Valid @RequestBody DecomposeRequest request) {
        return service.decompose(request);
    }
}
