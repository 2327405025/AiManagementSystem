package com.example.taskmanager.controller;

import com.example.taskmanager.ai.AsyncAiFacade;
import com.example.taskmanager.dto.AiDtos.DecomposeRequest;
import com.example.taskmanager.dto.AiDtos.Decomposition;
import com.example.taskmanager.dto.AiDtos.NaturalLanguageRequest;
import com.example.taskmanager.dto.AiDtos.TaskSuggestion;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/ai")
public class AiController {
    private final AsyncAiFacade service;

    public AiController(AsyncAiFacade service) {
        this.service = service;
    }

    @PostMapping("/parse-task")
    CompletableFuture<TaskSuggestion> parseTask(@Valid @RequestBody NaturalLanguageRequest request) {
        return service.parse(request.text());
    }

    @PostMapping("/decompose")
    CompletableFuture<Decomposition> decompose(@Valid @RequestBody DecomposeRequest request) {
        return service.decompose(request);
    }
}
