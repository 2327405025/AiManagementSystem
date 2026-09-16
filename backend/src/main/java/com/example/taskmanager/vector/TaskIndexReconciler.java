package com.example.taskmanager.vector;

import com.example.taskmanager.domain.Task;
import com.example.taskmanager.repository.TaskRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Component
public class TaskIndexReconciler {
    private final TaskRepository repository;
    private final ChromaTaskIndex index;
    private final boolean enabled;

    public TaskIndexReconciler(
            TaskRepository repository,
            ChromaTaskIndex index,
            @Value("${app.vector.enabled:false}") boolean enabled) {
        this.repository = repository;
        this.index = index;
        this.enabled = enabled;
    }

    @Scheduled(
            fixedDelayString = "${app.vector.reconcile-delay}",
            initialDelayString = "${app.vector.reconcile-delay}")
    @Transactional(readOnly = true)
    public void reconcileRecentlyChanged() {
        if (!enabled) {
            return;
        }
        repository.findAll(PageRequest.of(0, 200, Sort.by(Sort.Direction.DESC, "updatedAt")))
                .forEach(task -> index.handle(event(task)));
    }

    private TaskIndexEvent event(Task task) {
        String document = task.getTitle() + "\n"
                + (task.getDescription() == null ? "" : task.getDescription()) + "\n"
                + String.join(" ", task.getTags());
        return new TaskIndexEvent(task.getId(), document,
                Map.of("status", task.getStatus().value(), "priority", task.getPriority().value()),
                TaskIndexEvent.Operation.UPSERT);
    }
}
