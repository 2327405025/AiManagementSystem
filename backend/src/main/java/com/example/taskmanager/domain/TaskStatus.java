package com.example.taskmanager.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

public enum TaskStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED;

    @JsonCreator
    public static TaskStatus from(String value) {
        return valueOf(value.toUpperCase(Locale.ROOT));
    }

    @JsonValue
    public String value() {
        return name().toLowerCase(Locale.ROOT);
    }
}
