package talan.pfe.rulengine.exception;

import org.springframework.http.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.*;
import org.springframework.validation.FieldError;
import talan.pfe.rulengine.exception.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

@RestControllerAdvice
public class GlobalExceptionHandler {

    // ─── VALIDATION ERRORS (400) ────────────────────────────
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(
            MethodArgumentNotValidException ex) {

        Map<String, String> errors = new LinkedHashMap<>();
        ex.getBindingResult()
                .getAllErrors()
                .forEach(error -> {
                    String field = ((FieldError) error).getField();
                    String message = error.getDefaultMessage();
                    errors.put(field, message);
                });

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.builder()
                        .status(400)
                        .error("Validation Failed")
                        .message("One or more fields are invalid")
                        .details(errors)
                        .timestamp(LocalDateTime.now())
                        .build());
    }

    // ─── BAD REQUEST (400) ──────────────────────────────────
    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(
            BadRequestException ex) {
        return buildResponse(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    // ─── NOT FOUND (404) ────────────────────────────────────
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(
            ResourceNotFoundException ex) {
        return buildResponse(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    // ─── CONFLICT (409) ─────────────────────────────────────
    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ErrorResponse> handleConflict(
            ConflictException ex) {
        return buildResponse(HttpStatus.CONFLICT, ex.getMessage());
    }

    // ─── UNAUTHORIZED (401) ─────────────────────────────────
    @ExceptionHandler({
            BadCredentialsException.class,
            TokenException.class
    })
    public ResponseEntity<ErrorResponse> handleUnauthorized(
            RuntimeException ex) {
        return buildResponse(HttpStatus.UNAUTHORIZED, ex.getMessage());
    }

    // ─── FORBIDDEN (403) ────────────────────────────────────
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleForbidden(
            AccessDeniedException ex) {
        return buildResponse(HttpStatus.FORBIDDEN,
                "You do not have permission to access this resource");
    }

    // ─── ILLEGAL ARGUMENT (400) ─────────────────────────────
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(
            IllegalArgumentException ex) {
        return buildResponse(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    // ─── GENERIC FALLBACK (500) ─────────────────────────────
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex) {
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred. Please try again later.");
    }

    // ─── HELPER ─────────────────────────────────────────────
    private ResponseEntity<ErrorResponse> buildResponse(
            HttpStatus status, String message) {
        return ResponseEntity
                .status(status)
                .body(ErrorResponse.builder()
                        .status(status.value())
                        .error(status.getReasonPhrase())
                        .message(message)
                        .timestamp(LocalDateTime.now())
                        .build());
    }
}