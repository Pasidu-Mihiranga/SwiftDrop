package lk.swiftdrop.pricing.api.v1.admin;

import lk.swiftdrop.common.security.KeycloakRealmRoleConverter;
import lk.swiftdrop.common.security.SwiftDropSecurityAutoConfiguration;
import lk.swiftdrop.common.web.SwiftDropWebAutoConfiguration;
import lk.swiftdrop.common.web.error.BusinessRuleViolationException;
import lk.swiftdrop.common.web.error.ConflictException;
import lk.swiftdrop.common.web.error.ResourceNotFoundException;
import lk.swiftdrop.pricing.application.command.RateCardAdminService;
import lk.swiftdrop.pricing.application.model.Paged;
import lk.swiftdrop.pricing.application.model.RateCardFilter;
import lk.swiftdrop.pricing.application.query.RateCardQueryService;
import lk.swiftdrop.pricing.domain.model.RateCard;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminRateCardController.class)
@ImportAutoConfiguration({SwiftDropWebAutoConfiguration.class, SwiftDropSecurityAutoConfiguration.class})
class AdminRateCardControllerTest {

    private static final LocalDate JANUARY = LocalDate.of(2026, 1, 1);

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private RateCardQueryService queries;

    @MockitoBean
    private RateCardAdminService commands;

    private static RequestPostProcessor callerWithRoles(String... roles) {
        return jwt()
                .jwt(token -> token.subject("admin-1").claim("realm_access", Map.of("roles", List.of(roles))))
                .authorities(new KeycloakRealmRoleConverter());
    }

    private static RateCard card(UUID id, LocalDate effectiveTo) {
        return new RateCard(id, ZoneCode.of("WP"), ZoneCode.of("CP"), new BigDecimal("475.00"),
                new BigDecimal("78.00"), new BigDecimal("1.500"), new BigDecimal("0.0150"),
                JANUARY, effectiveTo);
    }

    private static final String VALID_BODY = """
            {"fromZone":"WP","toZone":"CP","baseFee":475.00,"perKgFee":78.00,
             "expressMultiplier":1.500,"codFeePct":0.0150,"effectiveFrom":"2026-01-01"}
            """;

    @Test
    void listsRateCardsWithTheSharedPaginationEnvelope() throws Exception {
        when(queries.list(any(), anyInt(), anyInt()))
                .thenReturn(new Paged<>(List.of(card(UUID.randomUUID(), null)), 0, 20, 81));

        mvc.perform(get("/api/v1/admin/pricing/rate-cards").with(callerWithRoles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].fromZone").value("WP"))
                .andExpect(jsonPath("$.content[0].effectiveTo").doesNotExist())
                .andExpect(jsonPath("$.page.totalElements").value(81))
                .andExpect(jsonPath("$.page.totalPages").value(5));
    }

    @Test
    void appliesTheZoneAndDateFiltersItWasGiven() throws Exception {
        when(queries.list(any(), anyInt(), anyInt())).thenReturn(new Paged<>(List.of(), 0, 20, 0));

        mvc.perform(get("/api/v1/admin/pricing/rate-cards")
                        .param("fromZone", "WP").param("toZone", "CP").param("activeOn", "2026-06-01")
                        .param("page", "2").param("size", "5")
                        .with(callerWithRoles("ADMIN")))
                .andExpect(status().isOk());

        ArgumentCaptor<RateCardFilter> filter = ArgumentCaptor.forClass(RateCardFilter.class);
        verify(queries).list(filter.capture(), org.mockito.ArgumentMatchers.eq(2),
                org.mockito.ArgumentMatchers.eq(5));
        assertThat(filter.getValue().fromZone()).isEqualTo(ZoneCode.of("WP"));
        assertThat(filter.getValue().toZone()).isEqualTo(ZoneCode.of("CP"));
        assertThat(filter.getValue().activeOn()).isEqualTo(LocalDate.of(2026, 6, 1));
    }

    @Test
    void treatsBlankFiltersAsNoFilterAtAll() throws Exception {
        when(queries.list(any(), anyInt(), anyInt())).thenReturn(new Paged<>(List.of(), 0, 20, 0));

        mvc.perform(get("/api/v1/admin/pricing/rate-cards").param("fromZone", "")
                        .with(callerWithRoles("ADMIN")))
                .andExpect(status().isOk());

        ArgumentCaptor<RateCardFilter> filter = ArgumentCaptor.forClass(RateCardFilter.class);
        verify(queries).list(filter.capture(), anyInt(), anyInt());
        assertThat(filter.getValue().fromZone()).isNull();
    }

    @Test
    void rejectsAMalformedZoneFilterAsBadInputRatherThanAServerError() throws Exception {
        mvc.perform(get("/api/v1/admin/pricing/rate-cards").param("fromZone", "not a zone")
                        .with(callerWithRoles("ADMIN")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ZONE_CODE"));
    }

    @Test
    void rejectsAPageSizeOutsideTheContractLimits() throws Exception {
        mvc.perform(get("/api/v1/admin/pricing/rate-cards").param("size", "500")
                        .with(callerWithRoles("ADMIN")))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/admin/pricing/rate-cards").param("page", "-1")
                        .with(callerWithRoles("ADMIN")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void creatingARateCardReturnsItsLocation() throws Exception {
        UUID id = UUID.randomUUID();
        when(commands.create(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(card(id, null));

        mvc.perform(post("/api/v1/admin/pricing/rate-cards")
                        .with(callerWithRoles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/admin/pricing/rate-cards/" + id))
                .andExpect(jsonPath("$.codFeePct").value(0.015));
    }

    @Test
    void rejectsACodFractionAboveOneAtTheApiBoundary() throws Exception {
        mvc.perform(post("/api/v1/admin/pricing/rate-cards")
                        .with(callerWithRoles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY.replace("0.0150", "1.5000")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("codFeePct"));
    }

    @Test
    void rejectsAMissingStartDate() throws Exception {
        mvc.perform(post("/api/v1/admin/pricing/rate-cards")
                        .with(callerWithRoles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY.replace(",\"effectiveFrom\":\"2026-01-01\"", "")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void reportsAnOverlappingPeriodAsAConflict() throws Exception {
        when(commands.create(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new ConflictException("RATE_CARD_OVERLAP",
                        "A rate card for WP to CP is already in force from 2026-01-01"));

        mvc.perform(post("/api/v1/admin/pricing/rate-cards")
                        .with(callerWithRoles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RATE_CARD_OVERLAP"));
    }

    @Test
    void reportsAnUnknownZoneAsNotFound() throws Exception {
        when(commands.create(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(ResourceNotFoundException.of("Zone", "CP"));

        mvc.perform(post("/api/v1/admin/pricing/rate-cards")
                        .with(callerWithRoles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ZONE_NOT_FOUND"));
    }

    @Test
    void returnsOneRateCard() throws Exception {
        UUID id = UUID.randomUUID();
        when(queries.find(id)).thenReturn(card(id, LocalDate.of(2026, 7, 1)));

        mvc.perform(get("/api/v1/admin/pricing/rate-cards/" + id).with(callerWithRoles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.effectiveTo").value("2026-07-01"));
    }

    @Test
    void reportsAnUnknownRateCardAsNotFound() throws Exception {
        UUID missing = UUID.randomUUID();
        when(queries.find(missing)).thenThrow(ResourceNotFoundException.of("Rate card", missing));

        mvc.perform(get("/api/v1/admin/pricing/rate-cards/" + missing).with(callerWithRoles("ADMIN")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RATE_CARD_NOT_FOUND"));
    }

    @Test
    void closesARateCard() throws Exception {
        UUID id = UUID.randomUUID();
        when(commands.close(any(), any())).thenReturn(card(id, LocalDate.of(2026, 7, 1)));

        mvc.perform(put("/api/v1/admin/pricing/rate-cards/" + id + "/closure")
                        .with(callerWithRoles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"effectiveTo\":\"2026-07-01\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.effectiveTo").value("2026-07-01"));
    }

    @Test
    void refusesToCloseACardBeforeItStarted() throws Exception {
        when(commands.close(any(), any()))
                .thenThrow(new BusinessRuleViolationException("INVALID_EFFECTIVE_RANGE",
                        "A rate card must end after 2026-01-01"));

        mvc.perform(put("/api/v1/admin/pricing/rate-cards/" + UUID.randomUUID() + "/closure")
                        .with(callerWithRoles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"effectiveTo\":\"2025-01-01\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INVALID_EFFECTIVE_RANGE"));
    }

    @Test
    void requiresAnEndDateWhenClosing() throws Exception {
        mvc.perform(put("/api/v1/admin/pricing/rate-cards/" + UUID.randomUUID() + "/closure")
                        .with(callerWithRoles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void keepsNonAdministratorsOutOfRateCardConfiguration() throws Exception {
        for (String role : List.of("CUSTOMER", "COURIER", "DISPATCHER", "MERCHANT")) {
            mvc.perform(get("/api/v1/admin/pricing/rate-cards").with(callerWithRoles(role)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));
            mvc.perform(post("/api/v1/admin/pricing/rate-cards")
                            .with(callerWithRoles(role))
                            .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                    .andExpect(status().isForbidden());
            mvc.perform(put("/api/v1/admin/pricing/rate-cards/" + UUID.randomUUID() + "/closure")
                            .with(callerWithRoles(role))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"effectiveTo\":\"2026-07-01\"}"))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void refusesAnonymousAccessToRateCardConfiguration() throws Exception {
        mvc.perform(get("/api/v1/admin/pricing/rate-cards"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }
}
