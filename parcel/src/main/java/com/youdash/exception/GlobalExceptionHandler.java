package com.youdash.exception;

import com.youdash.bean.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(ResourceNotFoundException ex) {
        ApiResponse<Void> r = new ApiResponse<>();
        r.setSuccess(false);
        r.setMessage(ex.getMessage());
        r.setMessageKey("NOT_FOUND");
        r.setStatus(HttpStatus.NOT_FOUND.value());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(r);
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(BadRequestException ex) {
        ApiResponse<Void> r = new ApiResponse<>();
        r.setSuccess(false);
        r.setMessage(ex.getMessage());
        r.setMessageKey("BAD_REQUEST");
        r.setStatus(HttpStatus.BAD_REQUEST.value());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(r);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
                .map(this::formatFieldError)
                .collect(Collectors.joining("; "));
        if (msg.isEmpty()) {
            msg = "Validation failed";
        }
        ApiResponse<Void> r = new ApiResponse<>();
        r.setSuccess(false);
        r.setMessage(msg);
        r.setMessageKey("VALIDATION_ERROR");
        r.setStatus(HttpStatus.BAD_REQUEST.value());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(r);
    }

    private String formatFieldError(FieldError fe) {
        return fe.getField() + ": " + (fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "invalid");
    }

    /**
     * Fallback for uncaught runtime errors (e.g. wallet settlement).
     * Response body: {@code { "message", "messageKey": "ERROR", "success": false, "status": 500 }} via {@link ApiResponse}.
     * {@link BadRequestException} and other more-specific handlers take precedence (subclass match).
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiResponse<Void>> handleRuntime(RuntimeException ex) {
        ApiResponse<Void> r = new ApiResponse<>();
        r.setMessage(ex.getMessage() != null ? ex.getMessage() : "Error");
        r.setMessageKey("ERROR");
        r.setSuccess(false);
        r.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(r);
    }
}
