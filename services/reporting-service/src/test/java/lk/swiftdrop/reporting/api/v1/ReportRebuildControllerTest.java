package lk.swiftdrop.reporting.api.v1;

import lk.swiftdrop.common.security.KeycloakRealmRoleConverter;
import lk.swiftdrop.common.security.SwiftDropSecurityAutoConfiguration;
import lk.swiftdrop.common.web.SwiftDropWebAutoConfiguration;
import lk.swiftdrop.reporting.application.command.RebuildService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReportRebuildController.class)
@ImportAutoConfiguration({SwiftDropWebAutoConfiguration.class, SwiftDropSecurityAutoConfiguration.class})
class ReportRebuildControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private RebuildService rebuild;

    @Test
    void anAdminStartsARebuild() throws Exception {
        mvc.perform(post("/api/v1/reports/rebuild").with(as("ADMIN")))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("REBUILDING"));
        verify(rebuild).rebuild();
    }

    @ParameterizedTest
    @ValueSource(strings = {"DISPATCHER", "CUSTOMER", "COURIER", "MERCHANT"})
    void nobodyElseMay(String role) throws Exception {
        mvc.perform(post("/api/v1/reports/rebuild").with(as(role)))
                .andExpect(status().isForbidden());
        verify(rebuild, never()).rebuild();
    }

    @Test
    void anonymousCallersMustSignIn() throws Exception {
        mvc.perform(post("/api/v1/reports/rebuild")).andExpect(status().isUnauthorized());
    }

    private static RequestPostProcessor as(String role) {
        return jwt().jwt(token -> token.subject("user-1").claim("realm_access", Map.of("roles", List.of(role))))
                .authorities(new KeycloakRealmRoleConverter());
    }
}
