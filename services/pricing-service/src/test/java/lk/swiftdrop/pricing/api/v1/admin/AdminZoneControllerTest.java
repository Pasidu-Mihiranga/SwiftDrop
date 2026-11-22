package lk.swiftdrop.pricing.api.v1.admin;

import lk.swiftdrop.common.security.KeycloakRealmRoleConverter;
import lk.swiftdrop.common.security.SwiftDropSecurityAutoConfiguration;
import lk.swiftdrop.common.web.SwiftDropWebAutoConfiguration;
import lk.swiftdrop.pricing.application.query.ZoneQueryService;
import lk.swiftdrop.pricing.domain.model.DistrictAssignment;
import lk.swiftdrop.pricing.domain.model.Zone;
import lk.swiftdrop.pricing.domain.model.ZoneCode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminZoneController.class)
@ImportAutoConfiguration({SwiftDropWebAutoConfiguration.class, SwiftDropSecurityAutoConfiguration.class})
class AdminZoneControllerTest {

    private static final ZoneCode CENTRAL = ZoneCode.of("CP");

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private ZoneQueryService queries;

    /** A token carrying the given realm roles, converted exactly as the service does at runtime. */
    private static RequestPostProcessor callerWithRoles(String... roles) {
        return jwt()
                .jwt(token -> token.subject("admin-1").claim("realm_access", java.util.Map.of("roles", List.of(roles))))
                .authorities(new KeycloakRealmRoleConverter());
    }

    @Test
    void anAdministratorSeesEveryZoneIncludingDeactivatedOnes() throws Exception {
        when(queries.allZones()).thenReturn(List.of(
                new Zone(UUID.randomUUID(), CENTRAL, "Central", false, List.of())));

        mvc.perform(get("/api/v1/admin/pricing/zones").with(callerWithRoles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("CP"))
                .andExpect(jsonPath("$[0].active").value(false))
                .andExpect(jsonPath("$[0].id").exists());
    }

    @Test
    void dispatcherCanResolveZoneIdsWithoutAccessToOtherPricingConfiguration() throws Exception {
        UUID zoneId = UUID.randomUUID();
        when(queries.allZones()).thenReturn(List.of(new Zone(zoneId, CENTRAL, "Central", true, List.of())));
        mvc.perform(get("/api/v1/admin/pricing/zones").with(callerWithRoles("DISPATCHER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(zoneId.toString()));
        mvc.perform(get("/api/v1/admin/pricing/districts").with(callerWithRoles("DISPATCHER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void zoneAndDistrictWritesAreGone() throws Exception {
        UUID id = UUID.randomUUID();
        mvc.perform(post("/api/v1/admin/pricing/zones").with(callerWithRoles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"CP\",\"name\":\"Central\"}"))
                .andExpect(status().isMethodNotAllowed());
        mvc.perform(put("/api/v1/admin/pricing/zones/" + id).with(callerWithRoles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Central\",\"active\":false}"))
                .andExpect(status().is4xxClientError());
        mvc.perform(put("/api/v1/admin/pricing/districts/Kandy").with(callerWithRoles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"zoneCode\":\"CP\"}"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void listsEveryDistrictAssignment() throws Exception {
        when(queries.allDistrictAssignments())
                .thenReturn(List.of(new DistrictAssignment("Kandy", CENTRAL, "Central")));

        mvc.perform(get("/api/v1/admin/pricing/districts").with(callerWithRoles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].zoneName").value("Central"));
    }

    @Test
    void keepsNonAdministratorsOutOfPricingConfiguration() throws Exception {
        for (String role : List.of("CUSTOMER", "COURIER", "MERCHANT")) {
            mvc.perform(get("/api/v1/admin/pricing/zones").with(callerWithRoles(role)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        }
    }

    @Test
    void refusesAnonymousAccessToPricingConfiguration() throws Exception {
        mvc.perform(MockMvcRequestBuilders.get("/api/v1/admin/pricing/zones"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }
}
