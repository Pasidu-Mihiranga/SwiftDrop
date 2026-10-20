package lk.swiftdrop.common.security.testapp;

import lk.swiftdrop.common.security.AuthenticatedUser;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Minimal app that exercises the security auto-configuration exactly like a real service. */
@SpringBootConfiguration
@EnableAutoConfiguration
@Import(SecurityTestApplication.TestController.class)
public class SecurityTestApplication {

    /** Real bearer tokens are rejected; tests use spring-security-test's jwt() post-processor instead. */
    @Bean
    JwtDecoder jwtDecoder() {
        return token -> {
            throw new BadJwtException("Signature invalid");
        };
    }

    @RestController
    static class TestController {

        @GetMapping("/public/ping")
        Map<String, String> ping() {
            return Map.of("status", "ok");
        }

        @GetMapping("/me")
        AuthenticatedUser me(AuthenticatedUser user) {
            return user;
        }

        @GetMapping("/courier/run-sheet")
        @PreAuthorize("hasRole('COURIER')")
        Map<String, String> runSheet(AuthenticatedUser user) {
            return Map.of("courierId", user.id());
        }
    }
}
