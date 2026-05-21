package talan.pfe.rulengine.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import static org.assertj.core.api.Assertions.*;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    // ─── BAD REQUEST ─────────────────────────────────────────

    @Test
    void handleBadRequest_returns400WithMessage() {
        ResponseEntity<ErrorResponse> resp =
                handler.handleBadRequest(new BadRequestException("Invalid input"));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().getMessage()).isEqualTo("Invalid input");
        assertThat(resp.getBody().getStatus()).isEqualTo(400);
    }

    // ─── NOT FOUND ───────────────────────────────────────────

    @Test
    void handleNotFound_returns404WithMessage() {
        ResponseEntity<ErrorResponse> resp =
                handler.handleNotFound(new ResourceNotFoundException("Resource not found"));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().getMessage()).isEqualTo("Resource not found");
        assertThat(resp.getBody().getStatus()).isEqualTo(404);
    }

    // ─── CONFLICT ────────────────────────────────────────────

    @Test
    void handleConflict_returns409WithMessage() {
        ResponseEntity<ErrorResponse> resp =
                handler.handleConflict(new ConflictException("Slug already taken"));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().getMessage()).isEqualTo("Slug already taken");
        assertThat(resp.getBody().getStatus()).isEqualTo(409);
    }

    // ─── UNAUTHORIZED ─────────────────────────────────────────

    @Test
    void handleUnauthorized_forBadCredentials_returns401() {
        ResponseEntity<ErrorResponse> resp =
                handler.handleUnauthorized(new BadCredentialsException("Invalid credentials"));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(resp.getBody().getStatus()).isEqualTo(401);
    }

    @Test
    void handleUnauthorized_forTokenException_returns401() {
        ResponseEntity<ErrorResponse> resp =
                handler.handleUnauthorized(new TokenException("Token expired"));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(resp.getBody().getMessage()).isEqualTo("Token expired");
    }

    // ─── FORBIDDEN ───────────────────────────────────────────

    @Test
    void handleForbidden_returns403WithFixedMessage() {
        ResponseEntity<ErrorResponse> resp =
                handler.handleForbidden(new AccessDeniedException("denied"));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(resp.getBody().getMessage())
                .isEqualTo("You do not have permission to access this resource");
        assertThat(resp.getBody().getStatus()).isEqualTo(403);
    }

    // ─── ILLEGAL ARGUMENT ─────────────────────────────────────

    @Test
    void handleIllegalArgument_returns400() {
        ResponseEntity<ErrorResponse> resp =
                handler.handleIllegalArgument(new IllegalArgumentException("Bad arg"));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody().getMessage()).isEqualTo("Bad arg");
    }

    // ─── VALIDATION ERROR ─────────────────────────────────────

    @Test
    void handleValidation_returns400WithFieldErrors() throws Exception {
        Object target = new Object();
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(target, "request");
        bindingResult.addError(new FieldError("request", "email", "Email is required"));
        bindingResult.addError(new FieldError("request", "password", "Password is required"));

        MethodArgumentNotValidException ex =
                new MethodArgumentNotValidException(null, bindingResult);

        ResponseEntity<ErrorResponse> resp = handler.handleValidation(ex);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody().getMessage()).isEqualTo("One or more fields are invalid");
        assertThat(resp.getBody().getDetails()).containsKey("email");
        assertThat(resp.getBody().getDetails()).containsKey("password");
        assertThat(resp.getBody().getDetails().get("email")).isEqualTo("Email is required");
    }

    // ─── GENERIC FALLBACK ─────────────────────────────────────

    @Test
    void handleGeneric_returns500WithErrorId() {
        ResponseEntity<ErrorResponse> resp =
                handler.handleGeneric(new RuntimeException("Unexpected crash"));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(resp.getBody().getStatus()).isEqualTo(500);
        assertThat(resp.getBody().getErrorId()).isNotBlank();
        assertThat(resp.getBody().getMessage()).contains("unexpected error");
    }

    // ─── RESPONSE STRUCTURE ───────────────────────────────────

    @Test
    void allResponses_haveTimestampSet() {
        ResponseEntity<ErrorResponse> resp =
                handler.handleBadRequest(new BadRequestException("x"));

        assertThat(resp.getBody().getTimestamp()).isNotNull();
    }

    @Test
    void allResponses_haveErrorReasonPhraseSet() {
        ResponseEntity<ErrorResponse> resp =
                handler.handleNotFound(new ResourceNotFoundException("x"));

        assertThat(resp.getBody().getError()).isEqualTo("Not Found");
    }
}