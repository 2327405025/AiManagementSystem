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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
        mvc.perform(post("/api/tasks").contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.title").exists());

        mvc.perform(get("/api/tasks").param("size", "101"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void providesAiFeaturesWithoutAnApiKey() throws Exception {
        mvc.perform(post("/api/ai/parse-task")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"提醒我明天下午3点买杂货\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("买杂货"))
                .andExpect(jsonPath("$.tags[0]").value("shopping"))
                .andExpect(jsonPath("$.source").value("rules"));

        mvc.perform(post("/api/ai/decompose")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"发布任务管理系统\"}"))
                .andExpect(status().isOk())
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
