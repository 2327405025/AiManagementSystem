package com.example.taskmanager.ai;

import com.example.taskmanager.domain.Priority;
import com.example.taskmanager.dto.AiDtos.DecomposeRequest;
import com.example.taskmanager.dto.AiDtos.Decomposition;
import com.example.taskmanager.dto.AiDtos.SubtaskSuggestion;
import com.example.taskmanager.dto.AiDtos.TaskSuggestion;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class AiService {
    private static final Pattern HOUR = Pattern.compile("(下午|晚上|pm)?\\s*(\\d{1,2})\\s*(?:点|:00)", Pattern.CASE_INSENSITIVE);
    private final ObjectMapper mapper;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private final String apiKey;
    private final String baseUrl;
    private final String model;
    private final CircuitBreaker circuitBreaker;

    public AiService(ObjectMapper mapper, CircuitBreakerRegistry circuitBreakerRegistry,
                     @Value("${app.ai.api-key:}") String apiKey,
                     @Value("${app.ai.base-url}") String baseUrl,
                     @Value("${app.ai.model}") String model) {
        this.mapper = mapper;
        this.apiKey = apiKey;
        this.baseUrl = baseUrl.replaceAll("/$", "");
        this.model = model;
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker("aiProvider");
    }

    public TaskSuggestion parse(String text) {
        if (!apiKey.isBlank()) {
            try {
                return circuitBreaker.executeSupplier(() -> {
                    String prompt = """
                            Extract a task from the user text. Return JSON only with fields:
                            title, description, dueAt (ISO-8601 instant or null),
                            priority (low|medium|high), tags (string array).
                            Current instant: %s
                            User text: %s
                            """.formatted(Instant.now(), text);
                    try {
                        TaskSuggestion result = mapper.readValue(call(prompt), TaskSuggestion.class);
                        return new TaskSuggestion(result.title(), result.description(), result.dueAt(),
                                result.priority(), result.tags(), "llm");
                    } catch (Exception exception) {
                        throw new IllegalStateException("AI provider request failed", exception);
                    }
                });
            } catch (RuntimeException ignored) {
                // Availability is more important than coupling task creation to an external provider.
            }
        }
        return ruleParse(text);
    }

    public Decomposition decompose(DecomposeRequest request) {
        if (!apiKey.isBlank()) {
            try {
                return circuitBreaker.executeSupplier(() -> {
                    String prompt = """
                            Break this task into 3-6 actionable subtasks. Return JSON only:
                            {"subtasks":[{"title":"...","priority":"low|medium|high","tags":["..."]}]}
                            Task: %s
                            Description: %s
                            """.formatted(request.title(), request.description());
                    try {
                        Decomposition result = mapper.readValue(call(prompt), Decomposition.class);
                        return new Decomposition(result.subtasks(), "llm");
                    } catch (Exception exception) {
                        throw new IllegalStateException("AI provider request failed", exception);
                    }
                });
            } catch (RuntimeException ignored) {
                // Fall through to deterministic suggestions.
            }
        }
        return ruleDecompose(request);
    }

    private String call(String prompt) throws Exception {
        String body = mapper.writeValueAsString(Map.of(
                "model", model,
                "temperature", 0.1,
                "response_format", Map.of("type", "json_object"),
                "messages", List.of(
                        Map.of("role", "system", "content", "You are a precise task planning assistant."),
                        Map.of("role", "user", "content", prompt))));
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/chat/completions"))
                .timeout(Duration.ofSeconds(12))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) {
            throw new IllegalStateException("AI provider returned " + response.statusCode());
        }
        JsonNode root = mapper.readTree(response.body());
        return root.at("/choices/0/message/content").asText();
    }

    private TaskSuggestion ruleParse(String text) {
        String normalized = text.trim();
        String lower = normalized.toLowerCase(Locale.ROOT);
        Priority priority = containsAny(lower, "紧急", "urgent", "asap", "重要") ? Priority.HIGH : Priority.MEDIUM;
        Set<String> tags = suggestedTags(lower);
        Instant dueAt = parseDueAt(normalized);
        String title = normalized
                .replaceFirst("^(请)?提醒我", "")
                .replaceFirst("(?i)^remind me (to )?", "")
                .replaceAll("今天|明天|后天", "")
                .replaceAll("(下午|上午|晚上)?\\s*\\d{1,2}\\s*(点|:00)", "")
                .trim();
        if (title.isBlank()) {
            title = normalized;
        }
        return new TaskSuggestion(title, null, dueAt, priority, tags, "rules");
    }

    private Instant parseDueAt(String text) {
        int days = text.contains("后天") ? 2 : text.contains("明天") || text.toLowerCase(Locale.ROOT).contains("tomorrow") ? 1 : 0;
        if (days == 0 && !text.contains("今天")) {
            return null;
        }
        int hour = 9;
        var matcher = HOUR.matcher(text);
        if (matcher.find()) {
            hour = Integer.parseInt(matcher.group(2));
            if (matcher.group(1) != null && hour < 12) {
                hour += 12;
            }
        }
        LocalDateTime dateTime = LocalDateTime.of(LocalDate.now().plusDays(days), LocalTime.of(hour, 0));
        return dateTime.atZone(ZoneId.systemDefault()).toInstant();
    }

    private Set<String> suggestedTags(String text) {
        Set<String> tags = new LinkedHashSet<>();
        if (containsAny(text, "代码", "开发", "api", "code", "bug")) tags.add("development");
        if (containsAny(text, "会议", "沟通", "meeting")) tags.add("meeting");
        if (containsAny(text, "买", "采购", "buy", "grocery")) tags.add("shopping");
        if (containsAny(text, "学习", "阅读", "study", "read")) tags.add("learning");
        if (tags.isEmpty()) tags.add("general");
        return tags;
    }

    private Decomposition ruleDecompose(DecomposeRequest request) {
        String subject = request.title().trim();
        List<SubtaskSuggestion> subtasks = List.of(
                new SubtaskSuggestion("明确范围与验收标准：" + subject, Priority.HIGH, Set.of("planning")),
                new SubtaskSuggestion("准备所需资料与环境", Priority.MEDIUM, Set.of("preparation")),
                new SubtaskSuggestion("执行核心工作：" + subject, Priority.HIGH, Set.of("execution")),
                new SubtaskSuggestion("检查结果并记录后续行动", Priority.MEDIUM, Set.of("review")));
        return new Decomposition(subtasks, "rules");
    }

    private boolean containsAny(String text, String... values) {
        for (String value : values) {
            if (text.contains(value)) return true;
        }
        return false;
    }
}
