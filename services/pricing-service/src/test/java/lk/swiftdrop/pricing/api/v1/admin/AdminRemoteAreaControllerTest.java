package lk.swiftdrop.pricing.api.v1.admin;

import lk.swiftdrop.common.security.KeycloakRealmRoleConverter;
import lk.swiftdrop.common.security.SwiftDropSecurityAutoConfiguration;
import lk.swiftdrop.common.web.SwiftDropWebAutoConfiguration;
import lk.swiftdrop.pricing.application.command.RemoteAreaAdminService;
import lk.swiftdrop.pricing.domain.model.RemoteAreaSurcharge;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminRemoteAreaController.class)
@ImportAutoConfiguration({SwiftDropWebAutoConfiguration.class, SwiftDropSecurityAutoConfiguration.class})
class AdminRemoteAreaControllerTest {

    private static final LocalDate JUNE = LocalDate.of(2026, 6, 1);

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    private static RequestPostProcessor callerWithRoles(String... roles) {
        return jwt()
                .jwt(token -> token.subject("user-1").claim("realm_access", Map.of("roles", List.of(roles))))
                .authorities(new KeycloakRealmRoleConverter());
    }

    @MockitoBean
    private RemoteAreaAdminService remoteAreas;

    @Test
    void listsChargesWithTheirPeriods() throws Exception {
        when(remoteAreas.list()).thenReturn(List.of(new RemoteAreaSurcharge("Mannar",
                new BigDecimal("150.00"), JUNE, LocalDate.of(2026, 7, 1))));

        mvc.perform(get("/api/v1/admin/pricing/remote-areas").with(callerWithRoles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].district").value("Mannar"))
                .andExpect(jsonPath("$[0].effectiveFrom").value("2026-06-01"))
                .andExpect(jsonPath("$[0].effectiveTo").value("2026-07-01"));
    }

    @Test
    void publishesACharge() throws Exception {
        when(remoteAreas.publish("Mannar", new BigDecimal("150.00"), JUNE))
                .thenReturn(new RemoteAreaSurcharge("Mannar", new BigDecimal("150.00"), JUNE, null));

        mvc.perform(put("/api/v1/admin/pricing/remote-areas/Mannar").with(callerWithRoles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"flatFee\":150.00,\"effectiveFrom\":\"2026-06-01\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.flatFee").value(150.00));
    }

    @Test
    void rejectsANegativeCharge() throws Exception {
        mvc.perform(put("/api/v1/admin/pricing/remote-areas/Mannar").with(callerWithRoles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"flatFee\":-1,\"effectiveFrom\":\"2026-06-01\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void refusesAnyoneButAnAdministrator() throws Exception {
        mvc.perform(get("/api/v1/admin/pricing/remote-areas").with(callerWithRoles("DISPATCHER")))
                .andExpect(status().isForbidden());
    }
}
