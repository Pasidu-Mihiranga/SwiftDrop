package lk.swiftdrop.common.security;

import lk.swiftdrop.common.web.error.ProblemDetails;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Turns Spring Security failures into problem responses. Runs before the catch-all handler in
 * {@code GlobalExceptionHandler} so access errors are never reported as 500s. Also used by the
 * filter chain's entry point and access-denied handler, so every 401/403 has the same shape.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SecurityExceptionAdvice {

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ProblemDetail> unauthorized(AuthenticationException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .header(HttpHeaders.WWW_AUTHENTICATE, "Bearer")
                .body(ProblemDetails.of(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "A valid access token is required."));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> forbidden(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ProblemDetails.of(HttpStatus.FORBIDDEN, "FORBIDDEN", "You do not have permission to perform this action."));
    }
}
