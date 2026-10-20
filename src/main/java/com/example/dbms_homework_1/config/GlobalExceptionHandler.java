package com.example.dbms_homework_1.config;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException e,
                                                                HttpServletRequest request) {
        List<Map<String, String>> fieldErrors = e.getBindingResult().getFieldErrors().stream()
                .map(this::toFieldError)
                .toList();
        String message = fieldErrors.stream()
                .map(f -> f.get("field") + ": " + f.get("message"))
                .findFirst()
                .orElse("Validation failed");
        log.warn("Validation failed for {}: {}", request.getRequestURI(), fieldErrors);
        return body(HttpStatus.BAD_REQUEST, message, request, fieldErrors);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleUnreadable(HttpMessageNotReadableException e,
                                                                HttpServletRequest request) {
        log.warn("Malformed JSON on {}: {}", request.getRequestURI(), e.getMessage());
        return body(HttpStatus.BAD_REQUEST, "Malformed JSON request body", request, null);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleTypeMismatch(MethodArgumentTypeMismatchException e,
                                                                  HttpServletRequest request) {
        String message = "Parameter '" + e.getName() + "' has invalid value '" + e.getValue() + "'";
        log.warn("Type mismatch on {}: {}", request.getRequestURI(), message);
        return body(HttpStatus.BAD_REQUEST, message, request, null);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(IllegalArgumentException e,
                                                              HttpServletRequest request) {
        log.warn("Not found on {}: {}", request.getRequestURI(), e.getMessage());
        return body(HttpStatus.NOT_FOUND, e.getMessage(), request, null);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleConflict(IllegalStateException e,
                                                              HttpServletRequest request) {
        log.warn("Conflict on {}: {}", request.getRequestURI(), e.getMessage());
        return body(HttpStatus.CONFLICT, e.getMessage(), request, null);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleStatus(ResponseStatusException e,
                                                            HttpServletRequest request) {
        HttpStatus status = HttpStatus.resolve(e.getStatusCode().value());
        if (status == null) {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }
        String message = e.getReason() != null ? e.getReason() : status.getReasonPhrase();
        log.warn("Status {} on {}: {}", status.value(), request.getRequestURI(), message);
        return body(status, message, request, null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception e, HttpServletRequest request) {
        log.error("Unhandled exception on {}", request.getRequestURI(), e);
        return body(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error", request, null);
    }

    private Map<String, String> toFieldError(FieldError error) {
        return Map.of(
                "field", error.getField(),
                "message", error.getDefaultMessage() == null ? "invalid value" : error.getDefaultMessage()
        );
    }

    private ResponseEntity<Map<String, Object>> body(HttpStatus status,
                                                     String message,
                                                     HttpServletRequest request,
                                                     List<Map<String, String>> fieldErrors) {
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message);
        body.put("path", request.getRequestURI());
        if (fieldErrors != null && !fieldErrors.isEmpty()) {
            body.put("fieldErrors", fieldErrors);
        }
        return ResponseEntity.status(status).body(body);
    }
}
