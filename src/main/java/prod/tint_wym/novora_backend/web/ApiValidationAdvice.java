package prod.tint_wym.novora_backend.web;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiValidationAdvice {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.putIfAbsent(fe.getField(), fe.getDefaultMessage());
        }
        String message =
                fieldErrors.entrySet().stream()
                        .map(e -> capitalizeField(e.getKey()) + ": " + e.getValue())
                        .collect(Collectors.joining(" "));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", message.isBlank() ? "Validation failed" : message);
        body.put("status", HttpStatus.BAD_REQUEST.value());
        body.put("errors", fieldErrors);
        return ResponseEntity.badRequest().body(body);
    }

    /**
     * AuthService.login calls {@code authenticationManager.authenticate(...)} directly (it does not
     * go through {@code UsernamePasswordAuthenticationFilter}), so {@link BadCredentialsException}
     * and other {@link AuthenticationException}s reach the dispatcher unmapped. Spring Boot's
     * default error handler then returns 500 with a stack trace — both a confusing UX ("Login
     * failed: Internal Server Error") and a leak of internals. Map them to a stable 401 here.
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Map<String, Object>> handleAuthFailure(AuthenticationException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        // Use a generic message regardless of root cause so we don't leak whether the email exists.
        body.put("message", "Invalid email or password");
        body.put("status", HttpStatus.UNAUTHORIZED.value());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
    }

    private static String capitalizeField(String field) {
        if (field == null || field.isBlank()) {
            return "Field";
        }
        return Character.toUpperCase(field.charAt(0)) + field.substring(1);
    }
}
