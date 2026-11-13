package com.dworf.slots_demo.exception;

import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatusException(ResponseStatusException ex) {
        HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());
        return buildErrorResponse(status, ex.getReason());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgumentException(IllegalArgumentException ex) {
        return buildErrorResponse(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleNotReadable(HttpMessageNotReadableException ex) {
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "Malformed request body");
    }

    /** Lost optimistic-lock race / deadlock victim: tell the client to retry, never leak SQL. */
    @ExceptionHandler(ConcurrencyFailureException.class)
    public ResponseEntity<Map<String, Object>> handleConcurrency(ConcurrencyFailureException ex) {
        log.warn("Concurrency conflict: {}", ex.getClass().getSimpleName());
        return buildErrorResponse(HttpStatus.CONFLICT, "The request conflicted with another operation, please retry");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleIntegrity(DataIntegrityViolationException ex) {
        log.warn("Integrity violation: {}", ex.getClass().getSimpleName());
        return buildErrorResponse(HttpStatus.CONFLICT, "The request conflicts with existing data");
    }

    /**
     * Framework errors (404/405/415...) keep their own status; anything else is a generic 500 with no internals.
     * A browser that closes a connection mid-download (typical for video range requests) is not a server error:
     * nobody is left to read a response, so it is dropped quietly instead of logged and re-serialised.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleAny(Exception ex, HttpServletResponse response) {
        if (isClientAbort(ex) || response.isCommitted()) {
            log.debug("Client went away or response already committed: {}", ex.getClass().getSimpleName());
            return null;
        }
        if (ex instanceof ErrorResponse er) {
            HttpStatus status = HttpStatus.valueOf(er.getStatusCode().value());
            return buildErrorResponse(status, status.getReasonPhrase());
        }
        log.error("Unhandled exception", ex);
        return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error");
    }

    private static boolean isClientAbort(Throwable ex) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            String name = t.getClass().getSimpleName();
            if (name.equals("ClientAbortException") || name.equals("AsyncRequestNotUsableException")) {
                return true;
            }
            String msg = t.getMessage();
            if (t instanceof java.io.IOException && msg != null
                    && (msg.contains("Broken pipe") || msg.contains("Connection reset")
                    || msg.contains("connection was aborted"))) {
                return true;
            }
            if (t.getCause() == t) break;
        }
        return false;
    }

    private ResponseEntity<Map<String, Object>> buildErrorResponse(HttpStatus status, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message);
        return ResponseEntity.status(status).body(body);
    }
}