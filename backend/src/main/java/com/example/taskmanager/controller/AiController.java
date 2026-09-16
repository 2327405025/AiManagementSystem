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

/**
 * Non-blocking HTTP endpoints for AI suggestions and task decomposition.
 * 提供 AI 建议与任务拆解的非阻塞 HTTP 端点。
 *
 * <p>Returned suggestions are never persisted automatically.</p>
 * <p>返回的建议不会被自动写入数据库。</p>
 */
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
