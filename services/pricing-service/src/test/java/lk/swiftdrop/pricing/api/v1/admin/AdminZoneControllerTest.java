package lk.swiftdrop.pricing.api.v1.admin;

import lk.swiftdrop.common.security.KeycloakRealmRoleConverter;
import lk.swiftdrop.common.security.SwiftDropSecurityAutoConfiguration;
import lk.swiftdrop.common.web.SwiftDropWebAutoConfiguration;
import lk.swiftdrop.common.web.error.BusinessRuleViolationException;
import lk.swiftdrop.common.web.error.ConflictException;
import lk.swiftdrop.common.web.error.ResourceNotFoundException;
import lk.swiftdrop.pricing.application.command.ZoneAdminService;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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

    @MockitoBean
    private ZoneAdminService commands;

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
    void creatingAZoneReturnsItsLocation() throws Exception {
        UUID id = UUID.randomUUID();
        when(commands.createZone(any(), any(), org.mockito.ArgumentMatchers.anyBoolean()))
                .thenReturn(new Zone(id, CENTRAL, "Central", true, List.of()));

        mvc.perform(post("/api/v1/admin/pricing/zones")
                        .with(callerWithRoles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"CP\",\"name\":\"Central\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/admin/pricing/zones/" + id))
                .andExpect(jsonPath("$.code").value("CP"));
    }

    @Test
    void rejectsAZoneCodeThatDoesNotMatchTheContract() throws Exception {
        mvc.perform(post("/api/v1/admin/pricing/zones")
                        .with(callerWithRoles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"central province\",\"name\":\"Central\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("code"));
    }

    @Test
    void reportsADuplicateZoneCodeAsAConflict() throws Exception {
        when(commands.createZone(any(), any(), org.mockito.ArgumentMatchers.anyBoolean()))
                .thenThrow(new ConflictException("ZONE_CODE_TAKEN", "A zone with code CP already exists"));

        mvc.perform(post("/api/v1/admin/pricing/zones")
                        .with(callerWithRoles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"CP\",\"name\":\"Central\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ZONE_CODE_TAKEN"));
    }

    @Test
    void reportsAnUnknownZoneAsNotFound() throws Exception {
        UUID missing = UUID.randomUUID();
        when(commands.findZone(missing)).thenThrow(ResourceNotFoundException.of("Zone", missing));

        mvc.perform(get("/api/v1/admin/pricing/zones/" + missing).with(callerWithRoles("ADMIN")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ZONE_NOT_FOUND"));
    }

    @Test
    void refusesToDeactivateAZoneThatStillServesDistricts() throws Exception {
        UUID id = UUID.randomUUID();
        when(commands.updateZone(any(), any(), org.mockito.ArgumentMatchers.anyBoolean()))
                .thenThrow(new BusinessRuleViolationException("ZONE_IN_USE", "Zone CP still has districts assigned"));

        mvc.perform(put("/api/v1/admin/pricing/zones/" + id)
                        .with(callerWithRoles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Central\",\"active\":false}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("ZONE_IN_USE"));
    }

    @Test
    void assignsADistrictToAZone() throws Exception {
        when(commands.assignDistrict(any(), any()))
                .thenReturn(new DistrictAssignment("Kandy", CENTRAL, "Central"));

        mvc.perform(put("/api/v1/admin/pricing/districts/Kandy")
                        .with(callerWithRoles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"zoneCode\":\"CP\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.district").value("Kandy"))
                .andExpect(jsonPath("$.zoneCode").value("CP"));
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
        for (String role : List.of("CUSTOMER", "COURIER", "DISPATCHER", "MERCHANT")) {
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
