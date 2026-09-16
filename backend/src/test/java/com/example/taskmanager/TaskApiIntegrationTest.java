package com.example.taskmanager;

import com.example.taskmanager.repository.TaskRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class TaskApiIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired TaskRepository repository;

    @BeforeEach
    void cleanDatabase() {
        repository.deleteAll();
    }

    @Test
    void createsFiltersUpdatesAndDeletesTask() throws Exception {
        long id = create("Ship API", "high", "backend");

        mvc.perform(get("/api/tasks").param("priority", "high").param("tag", "backend"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Ship API"));

        mvc.perform(put("/api/tasks/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Ship API","status":"completed","priority":"high","tags":["backend"]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("completed"));

        mvc.perform(delete("/api/tasks/{id}", id)).andExpect(status().isNoContent());
        mvc.perform(get("/api/tasks/{id}", id)).andExpect(status().isNotFound());
    }

    @Test
    void rejectsCompletionUntilDependenciesAreDoneAndRejectsCycles() throws Exception {
        long parent = create("Release", "high", "delivery");
        long dependency = create("Run tests", "medium", "testing");

        mvc.perform(post("/api/tasks/{taskId}/dependencies/{dependencyId}", parent, dependency))
                .andExpect(status().isOk());

        mvc.perform(put("/api/tasks/{id}", parent)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Release","status":"completed","priority":"high"}
                                """))
                .andExpect(status().isConflict());

        mvc.perform(post("/api/tasks/{taskId}/dependencies/{dependencyId}", dependency, parent))
                .andExpect(status().isConflict());

        mvc.perform(get("/api/tasks/{id}/dependency-tree", parent))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dependencies[0].id").value(dependency));
    }

    @Test
    void validatesInputAndPagination() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));

        mvc.perform(post("/api/tasks").contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.title").exists());

        mvc.perform(post("/api/tasks").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Task\",\"priority\":\"critical\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Malformed or unsupported field value"));

        mvc.perform(get("/api/tasks").param("size", "101"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void supportsCursorPaginationAndSemanticFallback() throws Exception {
        create("Alpha release", "high", "delivery");
        create("Beta review", "medium", "review");
        create("Gamma docs", "low", "docs");

        String firstPage = mvc.perform(get("/api/tasks/cursor").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.hasNext").value(true))
                .andReturn().getResponse().getContentAsString();
        String cursor = mapper.readTree(firstPage).get("nextCursor").asText();

        mvc.perform(get("/api/tasks/cursor").param("size", "2").param("after", cursor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.hasNext").value(false));

        mvc.perform(get("/api/tasks/semantic-search").param("query", "Alpha").param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("keyword_fallback"))
                .andExpect(jsonPath("$.content[0].title").value("Alpha release"));
    }

    @Test
    void invalidatesCacheAndRejectsStaleVersion() throws Exception {
        long id = create("Cached title", "medium", "cache");
        String loaded = mvc.perform(get("/api/tasks/{id}", id))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long version = mapper.readTree(loaded).get("version").asLong();

        mvc.perform(put("/api/tasks/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Updated title","priority":"medium","version":%d}
                                """.formatted(version)))
                .andExpect(status().isOk());

        mvc.perform(get("/api/tasks/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Updated title"));

        mvc.perform(put("/api/tasks/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Stale write","priority":"medium","version":%d}
                                """.formatted(version)))
                .andExpect(status().isConflict());
    }

    @Test
    void providesAiFeaturesWithoutAnApiKey() throws Exception {
        var parseResult = mvc.perform(post("/api/ai/parse-task")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"提醒我明天下午3点买杂货\"}"))
                .andExpect(request().asyncStarted())
                .andReturn();
        mvc.perform(asyncDispatch(parseResult))
                .andExpect(jsonPath("$.title").value("买杂货"))
                .andExpect(jsonPath("$.tags[0]").value("shopping"))
                .andExpect(jsonPath("$.source").value("rules"));

        var decomposeResult = mvc.perform(post("/api/ai/decompose")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"发布任务管理系统\"}"))
                .andExpect(request().asyncStarted())
                .andReturn();
        mvc.perform(asyncDispatch(decomposeResult))
                .andExpect(jsonPath("$.subtasks.length()").value(4))
                .andExpect(jsonPath("$.source").value("rules"));
    }

    private long create(String title, String priority, String tag) throws Exception {
        String body = mapper.writeValueAsString(
                java.util.Map.of("title", title, "priority", priority, "tags", java.util.List.of(tag)));
        String response = mvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode json = mapper.readTree(response);
        assertThat(json.get("status").asText()).isEqualTo("pending");
        return json.get("id").asLong();
    }
}
