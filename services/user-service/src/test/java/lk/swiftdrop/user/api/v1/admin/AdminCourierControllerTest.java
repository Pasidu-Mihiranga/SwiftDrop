package lk.swiftdrop.user.api.v1.admin;

import lk.swiftdrop.common.security.KeycloakRealmRoleConverter;
import lk.swiftdrop.common.security.SwiftDropSecurityAutoConfiguration;
import lk.swiftdrop.common.web.SwiftDropWebAutoConfiguration;
import lk.swiftdrop.common.web.error.ResourceNotFoundException;
import lk.swiftdrop.user.application.command.CourierProfileService;
import lk.swiftdrop.user.application.exception.IdentityProviderUnavailableException;
import lk.swiftdrop.user.application.query.CourierDirectoryService;
import lk.swiftdrop.user.domain.model.Courier;
import lk.swiftdrop.user.domain.model.CourierProfile;
import lk.swiftdrop.user.domain.model.VehicleType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminCourierController.class)
@ImportAutoConfiguration({SwiftDropWebAutoConfiguration.class, SwiftDropSecurityAutoConfiguration.class})
class AdminCourierControllerTest {

    private static final UUID KANDY = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final String VALID_BODY =
            "{\"vehicleType\":\"BIKE\",\"homeZoneId\":\"" + KANDY + "\",\"maxDailyCapacity\":25}";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private CourierDirectoryService directory;

    @MockitoBean
    private CourierProfileService profiles;

    private static RequestPostProcessor caller(String... roles) {
        return jwt()
                .jwt(token -> token.subject("staff-1").claim("realm_access", Map.of("roles", List.of(roles))))
                .authorities(new KeycloakRealmRoleConverter());
    }

    private static Courier withDetails() {
        return new Courier("c-1", "Chamath Courier", "courier1@swiftdrop.lk", true, Optional.of(
                new CourierProfile("c-1", VehicleType.BIKE, KANDY, 25, true, Instant.parse("2026-11-13T08:00:00Z"))));
    }

    @Test
    void dispatchersAndAdminsSeeTheCourierDirectory() throws Exception {
        when(directory.couriers(null, null)).thenReturn(List.of(withDetails(),
                new Courier("c-2", "Kasun Courier", "courier2@swiftdrop.lk", true, Optional.empty())));

        for (String role : List.of("DISPATCHER", "ADMIN")) {
            mvc.perform(get("/api/v1/admin/couriers").with(caller(role)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].id").value("c-1"))
                    .andExpect(jsonPath("$[0].fullName").value("Chamath Courier"))
                    .andExpect(jsonPath("$[0].vehicleType").value("BIKE"))
                    .andExpect(jsonPath("$[0].homeZoneId").value(KANDY.toString()))
                    .andExpect(jsonPath("$[0].maxDailyCapacity").value(25))
                    .andExpect(jsonPath("$[0].profileComplete").value(true))
                    .andExpect(jsonPath("$[0].assignable").value(true))
                    .andExpect(jsonPath("$[1].profileComplete").value(false))
                    .andExpect(jsonPath("$[1].assignable").value(false))
                    .andExpect(jsonPath("$[1].vehicleType").doesNotExist());
        }
    }

    @Test
    void passesTheZoneAndAssignableFiltersThrough() throws Exception {
        when(directory.couriers(KANDY, true)).thenReturn(List.of(withDetails()));

        mvc.perform(get("/api/v1/admin/couriers").param("zoneId", KANDY.toString()).param("assignable", "true")
                        .with(caller("DISPATCHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
        verify(directory).couriers(KANDY, true);
    }

    @Test
    void otherRolesCannotReadTheDirectory() throws Exception {
        for (String role : List.of("CUSTOMER", "COURIER", "MERCHANT", "MERCHANT_API")) {
            mvc.perform(get("/api/v1/admin/couriers").with(caller(role))).andExpect(status().isForbidden());
        }
        mvc.perform(get("/api/v1/admin/couriers")).andExpect(status().isUnauthorized());
        verifyNoInteractions(directory);
    }

    @Test
    void aBadZoneIdIsABadRequest() throws Exception {
        mvc.perform(get("/api/v1/admin/couriers").param("zoneId", "not-a-uuid").with(caller("ADMIN")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void keycloakBeingDownIsAServiceUnavailableProblem() throws Exception {
        when(directory.couriers(isNull(), isNull())).thenThrow(new IdentityProviderUnavailableException("down"));

        mvc.perform(get("/api/v1/admin/couriers").with(caller("DISPATCHER")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("IDENTITY_PROVIDER_UNAVAILABLE"));
    }

    @Test
    void anAdminSetsACouriersDetails() throws Exception {
        when(profiles.updateProfile(eq("c-1"), eq(VehicleType.BIKE), eq(KANDY), eq(25), eq(true))).thenReturn(withDetails());

        mvc.perform(put("/api/v1/admin/couriers/c-1").with(caller("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maxDailyCapacity").value(25))
                .andExpect(jsonPath("$.profileUpdatedAt").value("2026-11-13T08:00:00Z"));
    }

    @Test
    void anAdminCanTakeACourierOffDispatch() throws Exception {
        when(profiles.updateProfile(anyString(), any(), any(), anyInt(), anyBoolean())).thenReturn(withDetails());

        mvc.perform(put("/api/v1/admin/couriers/c-1").with(caller("ADMIN")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"vehicleType\":\"VAN\",\"homeZoneId\":\"" + KANDY + "\",\"maxDailyCapacity\":60,\"active\":false}"))
                .andExpect(status().isOk());
        verify(profiles).updateProfile("c-1", VehicleType.VAN, KANDY, 60, false);
    }

    @Test
    void onlyAdminsChangeDetails() throws Exception {
        mvc.perform(put("/api/v1/admin/couriers/c-1").with(caller("DISPATCHER"))
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isForbidden());
        verifyNoInteractions(profiles);
    }

    @Test
    void rejectsIncompleteOrOutOfRangeDetails() throws Exception {
        for (String body : List.of(
                "{\"homeZoneId\":\"" + KANDY + "\",\"maxDailyCapacity\":25}",
                "{\"vehicleType\":\"BIKE\",\"maxDailyCapacity\":25}",
                "{\"vehicleType\":\"BIKE\",\"homeZoneId\":\"" + KANDY + "\"}",
                "{\"vehicleType\":\"BIKE\",\"homeZoneId\":\"" + KANDY + "\",\"maxDailyCapacity\":0}",
                "{\"vehicleType\":\"BIKE\",\"homeZoneId\":\"" + KANDY + "\",\"maxDailyCapacity\":201}")) {
            mvc.perform(put("/api/v1/admin/couriers/c-1").with(caller("ADMIN"))
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        }
        mvc.perform(put("/api/v1/admin/couriers/c-1").with(caller("ADMIN")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"vehicleType\":\"PLANE\",\"homeZoneId\":\"" + KANDY + "\",\"maxDailyCapacity\":5}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(profiles);
    }

    @Test
    void anUnknownCourierIsNotFound() throws Exception {
        when(profiles.updateProfile(anyString(), any(), any(), anyInt(), anyBoolean()))
                .thenThrow(ResourceNotFoundException.of("Courier", "nobody"));

        mvc.perform(put("/api/v1/admin/couriers/nobody").with(caller("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COURIER_NOT_FOUND"));
    }
}
