package lk.swiftdrop.pricing.api.v1.admin;

import lk.swiftdrop.common.security.KeycloakRealmRoleConverter;
import lk.swiftdrop.common.security.SwiftDropSecurityAutoConfiguration;
import lk.swiftdrop.common.web.SwiftDropWebAutoConfiguration;
import lk.swiftdrop.pricing.application.command.PricingConfigurationAdminService;
import lk.swiftdrop.pricing.application.model.SameDayVehicleConfig;
import lk.swiftdrop.pricing.application.model.SpecialHandlingConfig;
import lk.swiftdrop.pricing.application.model.Versioned;
import lk.swiftdrop.pricing.domain.model.DistanceBand;
import lk.swiftdrop.pricing.domain.model.PricingSettings;
import lk.swiftdrop.pricing.domain.model.SameDayVehicle;
import lk.swiftdrop.pricing.domain.model.VehicleType;
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
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminPricingConfigurationController.class)
@ImportAutoConfiguration({SwiftDropWebAutoConfiguration.class, SwiftDropSecurityAutoConfiguration.class})
class AdminPricingConfigurationControllerTest {

    private static final LocalDate DAY = LocalDate.of(2031, 1, 1);

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private PricingConfigurationAdminService configuration;

    private static RequestPostProcessor callerWithRoles(String... roles) {
        return jwt()
                .jwt(token -> token.subject("user-1").claim("realm_access", Map.of("roles", List.of(roles))))
                .authorities(new KeycloakRealmRoleConverter());
    }

    @Test
    void listsSettingsWithTheirCutoffAndPeriod() throws Exception {
        when(configuration.settingsHistory()).thenReturn(List.of(new Versioned<>(new PricingSettings(UUID.randomUUID(),
                new BigDecimal("0.0500"), new BigDecimal("0.1800"), new BigDecimal("350.00"), LocalTime.of(14, 0)),
                DAY, null)));

        mvc.perform(get("/api/v1/admin/pricing/settings").with(callerWithRoles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].taxPct").value(0.18))
                .andExpect(jsonPath("$[0].sameDayCutoff").value("14:00:00"))
                .andExpect(jsonPath("$[0].effectiveFrom").value("2031-01-01"));
    }

    @Test
    void publishesSettingsWithAnHourAndMinuteCutoff() throws Exception {
        when(configuration.publishSettings(any(), any(), any(), eq(LocalTime.of(15, 30)), eq(DAY)))
                .thenReturn(new Versioned<>(new PricingSettings(UUID.randomUUID(), BigDecimal.ZERO, BigDecimal.ZERO,
                        BigDecimal.ZERO, LocalTime.of(15, 30)), DAY, null));

        mvc.perform(post("/api/v1/admin/pricing/settings").with(callerWithRoles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fuelSurchargePct\":0,\"taxPct\":0,\"minimumCharge\":0,"
                                + "\"sameDayCutoff\":\"15:30\",\"effectiveFrom\":\"2031-01-01\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sameDayCutoff").value("15:30:00"));
    }

    @Test
    void refusesAPercentageGivenAsAWholeNumber() throws Exception {
        mvc.perform(post("/api/v1/admin/pricing/settings").with(callerWithRoles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fuelSurchargePct\":5,\"taxPct\":0.18,\"minimumCharge\":350,"
                                + "\"effectiveFrom\":\"2031-01-01\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(configuration);
    }

    @Test
    void publishesADistanceBandSet() throws Exception {
        when(configuration.publishBands(anyList(), eq(DAY))).thenReturn(List.of(new Versioned<>(
                new DistanceBand(UUID.randomUUID(), BigDecimal.ZERO, null, BigDecimal.TEN, 2, 1), DAY, null)));

        mvc.perform(post("/api/v1/admin/pricing/distance-bands").with(callerWithRoles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"effectiveFrom\":\"2031-01-01\",\"bands\":[{\"minKm\":0,\"maxKm\":null,"
                                + "\"lineHaulFee\":10,\"standardTransitDays\":2,\"expressTransitDays\":1}]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$[0].lineHaulFee").value(10))
                .andExpect(jsonPath("$[0].maxKm").doesNotExist());

        mvc.perform(post("/api/v1/admin/pricing/distance-bands").with(callerWithRoles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"effectiveFrom\":\"2031-01-01\",\"bands\":[]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void addsAndEditsHandlingOptions() throws Exception {
        SpecialHandlingConfig oversize = new SpecialHandlingConfig("OVERSIZE", "Oversize", new BigDecimal("500"),
                true, 50);
        when(configuration.handlingOptions()).thenReturn(List.of(oversize));
        when(configuration.addHandling("OVERSIZE", "Oversize", new BigDecimal("500"), 50)).thenReturn(oversize);
        when(configuration.updateHandling(eq("OVERSIZE"), eq("Oversize"), any(), eq(false), eq(50)))
                .thenReturn(new SpecialHandlingConfig("OVERSIZE", "Oversize", new BigDecimal("500"), false, 50));

        mvc.perform(get("/api/v1/admin/pricing/special-handling").with(callerWithRoles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].fee").value(500));
        mvc.perform(post("/api/v1/admin/pricing/special-handling").with(callerWithRoles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"OVERSIZE\",\"label\":\"Oversize\",\"fee\":500,\"sortOrder\":50}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.active").value(true));
        mvc.perform(put("/api/v1/admin/pricing/special-handling/OVERSIZE").with(callerWithRoles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"label\":\"Oversize\",\"fee\":500,\"active\":false,\"sortOrder\":50}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void editsAVehicleButNotBeyondTheQuoteWeightCeiling() throws Exception {
        SameDayVehicleConfig van = new SameDayVehicleConfig(new SameDayVehicle(VehicleType.VAN,
                new BigDecimal("90"), new BigDecimal("120"), new BigDecimal("1.8")), "Van", true);
        when(configuration.sameDayVehicles()).thenReturn(List.of(van));
        when(configuration.updateVehicle(eq(VehicleType.VAN), eq("Van"), any(), any(), any(), eq(true)))
                .thenReturn(van);

        mvc.perform(get("/api/v1/admin/pricing/vehicles").with(callerWithRoles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("VAN"));
        String body = "{\"label\":\"Van\",\"maxWeightKg\":%s,\"maxDistanceKm\":120,\"rateMultiplier\":1.8,"
                + "\"active\":true}";
        mvc.perform(put("/api/v1/admin/pricing/vehicles/VAN").with(callerWithRoles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(body.formatted("90")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maxWeightKg").value(90));
        mvc.perform(put("/api/v1/admin/pricing/vehicles/VAN").with(callerWithRoles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(body.formatted("150")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void refusesAnyoneButAnAdministrator() throws Exception {
        mvc.perform(get("/api/v1/admin/pricing/settings").with(callerWithRoles("DISPATCHER")))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/admin/pricing/vehicles/VAN").with(callerWithRoles("CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"label\":\"Van\",\"maxWeightKg\":90,\"maxDistanceKm\":120,\"rateMultiplier\":1.8,"
                                + "\"active\":true}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(configuration);
    }
}
