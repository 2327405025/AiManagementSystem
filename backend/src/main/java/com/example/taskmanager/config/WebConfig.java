package com.example.taskmanager.config;

import com.example.taskmanager.domain.Priority;
import com.example.taskmanager.domain.TaskStatus;
import org.springframework.context.annotation.Configuration;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Keeps HTTP enum parsing consistent with lowercase JSON values and limits
 * development CORS access to the Vite origin.
 * 保持查询参数枚举与小写 JSON 值一致，并将开发环境 CORS 限制在 Vite 来源。
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {
    @Override
    public void addFormatters(FormatterRegistry registry) {
        registry.addConverter(String.class, TaskStatus.class, TaskStatus::from);
        registry.addConverter(String.class, Priority.class, Priority::from);
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins("http://localhost:5173")
                .allowedMethods("GET", "POST", "PUT", "DELETE");
    }
}
