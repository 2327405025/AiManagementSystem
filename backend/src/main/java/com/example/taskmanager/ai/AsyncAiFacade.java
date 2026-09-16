package com.example.taskmanager.ai;

import com.example.taskmanager.dto.AiDtos.DecomposeRequest;
import com.example.taskmanager.dto.AiDtos.Decomposition;
import com.example.taskmanager.dto.AiDtos.TaskSuggestion;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Service
public class AsyncAiFacade {
    private final AiService service;
    private final TaskExecutor executor;

    public AsyncAiFacade(AiService service, @Qualifier("aiExecutor") TaskExecutor executor) {
        this.service = service;
        this.executor = executor;
    }

    @RateLimiter(name = "aiApi")
    public CompletableFuture<TaskSuggestion> parse(String text) {
        return CompletableFuture.supplyAsync(() -> service.parse(text), executor);
    }

    @RateLimiter(name = "aiApi")
    public CompletableFuture<Decomposition> decompose(DecomposeRequest request) {
        return CompletableFuture.supplyAsync(() -> service.decompose(request), executor);
    }
}
