package com.moveinsync.config.server.exception;

import com.moveinsync.config.core.exception.UnknownSettingException;
import com.moveinsync.config.core.exception.ValidationFailedException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Collections;
import java.util.UUID;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private String getOrGenerateCorrelationId(HttpServletRequest request) {
        String cid = MDC.get("correlationId");
        if (cid == null || cid.isBlank()) {
            cid = request.getHeader("X-Correlation-ID");
        }
        if (cid == null || cid.isBlank()) {
            cid = UUID.randomUUID().toString();
        }
        return cid;
    }

    @ExceptionHandler(ValidationFailedException.class)
    public ResponseEntity<ErrorResponse> handleValidationFailed(ValidationFailedException ex, HttpServletRequest request) {
        String cid = getOrGenerateCorrelationId(request);
        log.warn("Validation failed [correlationId={}]: {}", cid, ex.violations());
        ErrorResponse body = new ErrorResponse("VALIDATION_FAILED", "Validation failed", cid, ex.violations());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(body);
    }

    @ExceptionHandler(UnknownSettingException.class)
    public ResponseEntity<ErrorResponse> handleUnknownSetting(UnknownSettingException ex, HttpServletRequest request) {
        String cid = getOrGenerateCorrelationId(request);
        log.warn("Unknown setting [correlationId={}]: {}", cid, ex.settingKey());
        ErrorResponse body = new ErrorResponse("UNKNOWN_SETTING", "Setting not found: " + ex.settingKey(), cid, Collections.emptyList());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    @ExceptionHandler(ConfigException.class)
    public ResponseEntity<ErrorResponse> handleConfigException(ConfigException ex, HttpServletRequest request) {
        String cid = getOrGenerateCorrelationId(request);
        log.warn("Config exception [correlationId={}] code={}: {}", cid, ex.code(), ex.getMessage());
        ErrorResponse body = new ErrorResponse(ex.code(), ex.getMessage(), cid, Collections.emptyList());
        return ResponseEntity.status(ex.status()).body(body);
    }

    @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFound(org.springframework.web.servlet.resource.NoResourceFoundException ex, HttpServletRequest request) {
        String cid = getOrGenerateCorrelationId(request);
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("NOT_FOUND", ex.getMessage(), cid, Collections.emptyList()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex, HttpServletRequest request) {
        String cid = getOrGenerateCorrelationId(request);
        log.error("Unhandled exception [correlationId={}]", cid, ex);
        // Error hygiene: never echo stack traces or SQL in response bodies!
        ErrorResponse body = new ErrorResponse("INTERNAL_ERROR", "An unexpected error occurred", cid, Collections.emptyList());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}

