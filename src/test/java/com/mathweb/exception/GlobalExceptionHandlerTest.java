package com.mathweb.exception;

import com.mathweb.dto.response.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.HttpInputMessage;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.http.HttpMethod;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Pure unit test: the handler is instantiated directly, no Spring context.
 * It pins down which HTTP status and message each exception turns into,
 * and guards against leaking internal details to clients.
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    private static void assertResponse(ResponseEntity<? extends ApiResponse<?>> response,
                                       HttpStatus status, String message) {
        assertThat(response.getStatusCode()).isEqualTo(status);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).hasFieldOrPropertyWithValue("message", message);
        assertThat(response.getBody()).hasFieldOrPropertyWithValue("success", false);
    }

    @Test
    void appException_usesItsOwnStatusAndMessage() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleAppException(new OtpException("Invalid or expired OTP"));

        assertResponse(response, HttpStatus.BAD_REQUEST, "Invalid or expired OTP");
    }

    @Test
    void categoryConflict_is409WithItsMessage() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleCategoryConflict(new CategoryConflictException("Slug already used"));

        assertResponse(response, HttpStatus.CONFLICT, "Slug already used");
    }

    @Test
    void badCredentials_is401WithGenericMessage_thatDoesNotRevealWhichPartWasWrong() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleBadCredentials(new BadCredentialsException("User not found: x@y.com"));

        assertResponse(response, HttpStatus.UNAUTHORIZED, "Invalid email or password");
    }

    @Test
    void lockedAccount_is401() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleDisabled(new LockedException("locked"));

        assertResponse(response, HttpStatus.UNAUTHORIZED, "Account is not verified or has been disabled");
    }

    @Test
    void disabledAccount_is401() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleDisabled(new DisabledException("disabled"));

        assertResponse(response, HttpStatus.UNAUTHORIZED, "Account is not verified or has been disabled");
    }

    @Test
    void accessDenied_is403() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleAccessDenied(new AccessDeniedException("nope"));

        assertResponse(response, HttpStatus.FORBIDDEN,
                "You do not have permission to access this resource");
    }

    @Test
    void uploadTooLarge_is413() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleMaxUploadSize(new MaxUploadSizeExceededException(1024));

        assertResponse(response, HttpStatus.PAYLOAD_TOO_LARGE,
                "File size exceeds the maximum allowed limit");
    }

    @Test
    void badMultipart_is400() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleBadUpload(new MultipartException("not multipart"));

        assertResponse(response, HttpStatus.BAD_REQUEST, "Invalid or missing upload data");
    }

    @Test
    void dataIntegrityViolation_is409_andDoesNotLeakSqlDetails() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleDataIntegrity(
                new DataIntegrityViolationException("duplicate key value violates unique constraint users_email_key"));

        assertResponse(response, HttpStatus.CONFLICT, "This operation conflicts with existing data");
        assertThat(response.getBody().getMessage()).doesNotContain("users_email_key");
    }

    @Test
    void unreadableBody_is400() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleUnreadable(
                new HttpMessageNotReadableException("bad json", mock(HttpInputMessage.class)));

        assertResponse(response, HttpStatus.BAD_REQUEST, "Malformed or missing request body");
    }

    @Test
    void noResourceFound_is404() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleNoResourceFound(
                new NoResourceFoundException(HttpMethod.GET, "/nothing"));

        assertResponse(response, HttpStatus.NOT_FOUND, "Resource not found");
    }

    @Test
    void unexpectedException_is500_andDoesNotLeakTheInternalMessage() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleGenericException(
                new IllegalStateException("password=hunter2 in jdbc:postgresql://db"));

        assertResponse(response, HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred. Please try again later.");
        assertThat(response.getBody().getMessage()).doesNotContain("hunter2");
    }

    @Test
    void validationErrors_are400_withOneEntryPerField() throws Exception {
        BeanPropertyBindingResult binding = new BeanPropertyBindingResult(new Object(), "registerRequest");
        binding.addError(new FieldError("registerRequest", "email", "Email is required"));
        binding.addError(new FieldError("registerRequest", "password", "Password is required"));
        MethodParameter parameter =
                new MethodParameter(Object.class.getDeclaredMethod("toString"), -1);

        ResponseEntity<ApiResponse<Map<String, String>>> response =
                handler.handleValidationErrors(new MethodArgumentNotValidException(parameter, binding));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getMessage()).isEqualTo("Validation failed");
        assertThat(response.getBody().getData())
                .containsEntry("email", "Email is required")
                .containsEntry("password", "Password is required");
    }
}