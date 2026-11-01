package lk.swiftdrop.pricing.api.v1.admin;

import lk.swiftdrop.common.security.KeycloakRealmRoleConverter;
import lk.swiftdrop.common.security.SwiftDropSecurityAutoConfiguration;
import lk.swiftdrop.common.web.SwiftDropWebAutoConfiguration;
import lk.swiftdrop.pricing.application.command.MerchantAgreementAdminService;
import lk.swiftdrop.pricing.domain.model.LaneOverride;
import lk.swiftdrop.pricing.domain.model.MerchantAgreement;
import lk.swiftdrop.pricing.domain.model.ZoneCode;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminMerchantAgreementController.class)
@ImportAutoConfiguration({SwiftDropWebAutoConfiguration.class, SwiftDropSecurityAutoConfiguration.class})
class AdminMerchantAgreementControllerTest {

    private static final UUID MERCHANT = UUID.fromString("7b9f2c1e-52c7-4f6e-9d1a-3b1c2d4e5f60");
    private static final LocalDate JUNE = LocalDate.of(2026, 6, 1);
    private static final String PATH = "/api/v1/admin/pricing/merchants/" + MERCHANT + "/agreements";

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
    private MerchantAgreementAdminService agreements;

    private static MerchantAgreement stored() {
        return new MerchantAgreement(UUID.randomUUID(), MERCHANT, new BigDecimal("0.1000"), BigDecimal.ZERO,
                true, false, false,
                List.of(new LaneOverride(ZoneCode.of("WP"), ZoneCode.of("CP"), null, new BigDecimal("300.00"))),
                JUNE, null);
    }

    @Test
    void listsWhoHasTermsToday() throws Exception {
        when(agreements.listInForce()).thenReturn(List.of(stored()));

        mvc.perform(get("/api/v1/admin/pricing/merchants").with(callerWithRoles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].merchantId").value(MERCHANT.toString()))
                .andExpect(jsonPath("$[0].effectiveFrom").value("2026-06-01"));
    }

    @Test
    void listsOneMerchantsHistory() throws Exception {
        when(agreements.history(MERCHANT)).thenReturn(List.of(stored()));

        mvc.perform(get(PATH).with(callerWithRoles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].laneOverrides[0].flatBaseFee").value(300.00));
    }

    @Test
    void publishesTermsWithTheirLanes() throws Exception {
        when(agreements.publish(any(), any())).thenReturn(stored());

        mvc.perform(put(PATH).with(callerWithRoles("ADMIN")).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"discountPct":0.1000,"waivesRemoteArea":true,"effectiveFrom":"2026-06-01",
                                 "laneOverrides":[{"fromZone":"WP","toZone":"CP","flatBaseFee":300.00}]}
                                """))
                .andExpect(status().isOk());

        ArgumentCaptor<MerchantAgreement> published = ArgumentCaptor.forClass(MerchantAgreement.class);
        verify(agreements).publish(published.capture(), eq(JUNE));
        assertThat(published.getValue().laneOverrides()).hasSize(1);
        assertThat(published.getValue().waivesRemoteArea()).isTrue();
    }

    @Test
    void rejectsALaneThatChangesNothingAsABadRequest() throws Exception {
        mvc.perform(put(PATH).with(callerWithRoles("ADMIN")).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"effectiveFrom":"2026-06-01","laneOverrides":[{"fromZone":"WP","toZone":"CP"}]}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void refusesAnyoneButAnAdministrator() throws Exception {
        mvc.perform(get("/api/v1/admin/pricing/merchants").with(callerWithRoles("MERCHANT")))
                .andExpect(status().isForbidden());
    }
}
