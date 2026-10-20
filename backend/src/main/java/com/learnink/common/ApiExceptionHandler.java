package com.learnink.common;

import com.learnink.progress.LevelLockedException;
import com.learnink.query.EngineUnavailableException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    ResponseEntity<ApiError> notFound(ResourceNotFoundException e) {
        return error(HttpStatus.NOT_FOUND, "NOT_FOUND", e.getMessage());
    }

    @ExceptionHandler(LevelLockedException.class)
    ResponseEntity<ApiError> levelLocked(LevelLockedException e) {
        return error(HttpStatus.FORBIDDEN, "LEVEL_LOCKED", e.getMessage());
    }

    @ExceptionHandler(InvalidRequestException.class)
    ResponseEntity<ApiError> invalid(InvalidRequestException e) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", e.getMessage());
    }

    @ExceptionHandler(BadCredentialsException.class)
    ResponseEntity<ApiError> badCredentials(BadCredentialsException e) {
        return error(HttpStatus.UNAUTHORIZED, "BAD_CREDENTIALS", e.getMessage());
    }

    @ExceptionHandler(EngineUnavailableException.class)
    ResponseEntity<ApiError> engineUnavailable(EngineUnavailableException e) {
        return error(HttpStatus.SERVICE_UNAVAILABLE, "ENGINE_UNAVAILABLE", e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> validation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> fieldError.getField() + " : " + fieldError.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message);
    }

    private ResponseEntity<ApiError> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(new ApiError(code, message));
    }
}
