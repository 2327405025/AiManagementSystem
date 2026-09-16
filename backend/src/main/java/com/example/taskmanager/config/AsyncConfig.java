package com.example.taskmanager.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

@Configuration
@EnableAsync
@EnableScheduling
public class AsyncConfig {
    @Bean
    @Qualifier("aiExecutor")
    TaskExecutor aiExecutor(
            @Value("${app.executors.ai.core-size}") int core,
            @Value("${app.executors.ai.max-size}") int max,
            @Value("${app.executors.ai.queue-capacity}") int queueCapacity) {
        return executor("ai-", core, max, queueCapacity, new ThreadPoolExecutor.AbortPolicy());
    }

    @Bean
    @Qualifier("indexingExecutor")
    TaskExecutor indexingExecutor(
            @Value("${app.executors.indexing.core-size}") int core,
            @Value("${app.executors.indexing.max-size}") int max,
            @Value("${app.executors.indexing.queue-capacity}") int queueCapacity) {
        return executor("vector-index-", core, max, queueCapacity, new ThreadPoolExecutor.CallerRunsPolicy());
    }

    private ThreadPoolTaskExecutor executor(
            String prefix,
            int core,
            int max,
            int queueCapacity,
            java.util.concurrent.RejectedExecutionHandler rejectionHandler) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix(prefix);
        executor.setCorePoolSize(core);
        executor.setMaxPoolSize(max);
        executor.setQueueCapacity(queueCapacity);
        executor.setKeepAliveSeconds(30);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(15);
        executor.setRejectedExecutionHandler(rejectionHandler);
        executor.initialize();
        return executor;
    }
}
