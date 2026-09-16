package com.example.taskmanager.exception;

import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.RejectedExecutionException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(ApiException.class)
    ResponseEntity<Problem> handleApi(ApiException exception, HttpServletRequest request) {
        return ResponseEntity.status(exception.getStatus())
                .body(problem(exception.getStatus().value(), exception.getMessage(), request.getRequestURI(), null));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Problem> handleValidation(MethodArgumentNotValidException exception, HttpServletRequest request) {
        Map<String, String> fields = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors()
                .forEach(error -> fields.putIfAbsent(error.getField(), error.getDefaultMessage()));
        return ResponseEntity.badRequest()
                .body(problem(400, "Request validation failed", request.getRequestURI(), fields));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Problem> handleBadInput(IllegalArgumentException exception, HttpServletRequest request) {
        return ResponseEntity.badRequest()
                .body(problem(400, exception.getMessage(), request.getRequestURI(), null));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<Problem> handleMalformedInput(Exception exception, HttpServletRequest request) {
        return ResponseEntity.badRequest()
                .body(problem(400, "Malformed or unsupported field value", request.getRequestURI(), null));
    }

    @ExceptionHandler(RejectedExecutionException.class)
    ResponseEntity<Problem> handleOverloaded(RejectedExecutionException exception, HttpServletRequest request) {
        return ResponseEntity.status(429)
                .body(problem(429, "AI service is busy; retry later", request.getRequestURI(), null));
    }

    @ExceptionHandler(RequestNotPermitted.class)
    ResponseEntity<Problem> handleRateLimited(RequestNotPermitted exception, HttpServletRequest request) {
        return ResponseEntity.status(429)
                .body(problem(429, "Rate limit exceeded; retry later", request.getRequestURI(), null));
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ResponseEntity<Problem> handleConcurrentUpdate(
            ObjectOptimisticLockingFailureException exception,
            HttpServletRequest request) {
        return ResponseEntity.status(409)
                .body(problem(409, "Task was modified concurrently; reload and retry", request.getRequestURI(), null));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<Problem> handleConstraintConflict(
            DataIntegrityViolationException exception,
            HttpServletRequest request) {
        return ResponseEntity.status(409)
                .body(problem(409, "Request conflicts with existing data", request.getRequestURI(), null));
    }

    private Problem problem(int status, String message, String path, Map<String, String> fields) {
        return new Problem(Instant.now(), status, message, path, fields);
    }

    record Problem(Instant timestamp, int status, String message, String path, Map<String, String> fields) {}
}
