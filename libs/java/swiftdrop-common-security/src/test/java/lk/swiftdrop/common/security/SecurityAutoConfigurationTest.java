package lk.swiftdrop.common.security;

import lk.swiftdrop.common.security.testapp.SecurityTestApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = SecurityTestApplication.class, properties = "swiftdrop.security.public-paths=/public/**")
@AutoConfigureMockMvc
class SecurityAutoConfigurationTest {

    @Autowired
    private MockMvc mvc;

    private static JwtRequestPostProcessor tokenWithRoles(String subject, String... roles) {
        return jwt()
                .jwt(builder -> builder.subject(subject)
                        .claim("email", subject + "@swiftdrop.lk")
                        .claim("realm_access", Map.of("roles", List.of(roles))))
                .authorities(new KeycloakRealmRoleConverter());
    }

    @Test
    void requestsWithoutTokenGet401Problem() throws Exception {
        mvc.perform(get("/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void invalidBearerTokenGets401Problem() throws Exception {
        mvc.perform(get("/me").header(HttpHeaders.AUTHORIZATION, "Bearer not-a-real-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void configuredPublicPathsNeedNoToken() throws Exception {
        mvc.perform(get("/public/ping")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ok"));
    }

    @Test
    void authenticatedUserIsResolvedFromToken() throws Exception {
        mvc.perform(get("/me").with(tokenWithRoles("user-42", "CUSTOMER", "offline_access", "default-roles-swiftdrop")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("user-42"))
                .andExpect(jsonPath("$.roles").value(org.hamcrest.Matchers.contains("CUSTOMER")));
    }

    @Test
    void roleProtectedEndpointAllowsMatchingRole() throws Exception {
        mvc.perform(get("/courier/run-sheet").with(tokenWithRoles("courier-1", "COURIER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.courierId").value("courier-1"));
    }

    @Test
    void roleProtectedEndpointRejectsOtherRolesWith403Problem() throws Exception {
        mvc.perform(get("/courier/run-sheet").with(tokenWithRoles("customer-1", "CUSTOMER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }
}
