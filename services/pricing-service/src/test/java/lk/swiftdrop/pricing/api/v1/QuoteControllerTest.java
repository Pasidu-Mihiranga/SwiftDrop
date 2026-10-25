package lk.swiftdrop.pricing.api.v1;

import lk.swiftdrop.common.security.SwiftDropSecurityAutoConfiguration;
import lk.swiftdrop.common.web.SwiftDropWebAutoConfiguration;
import lk.swiftdrop.common.web.error.BusinessRuleViolationException;
import lk.swiftdrop.pricing.application.command.QuoteIssuanceService;
import lk.swiftdrop.pricing.application.model.QuoteSnapshot;
import lk.swiftdrop.pricing.application.query.QuotePricingInput;
import lk.swiftdrop.pricing.application.query.QuoteLookupService;
import lk.swiftdrop.pricing.domain.model.FeeBreakdown;
import lk.swiftdrop.pricing.domain.model.PricingRule;
import lk.swiftdrop.pricing.domain.model.ServiceLevel;
import lk.swiftdrop.pricing.domain.model.SizeClassCode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(QuoteController.class)
@ImportAutoConfiguration({SwiftDropWebAutoConfiguration.class, SwiftDropSecurityAutoConfiguration.class})
class QuoteControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private QuoteIssuanceService quotes;

    @MockitoBean
    private QuoteLookupService lookup;

    @Test
    void issuesAContractShapedQuoteForAnAnonymousCustomer() throws Exception {
        UUID id = UUID.randomUUID();
        QuotePricingInput input = new QuotePricingInput("Colombo", "Kandy", new BigDecimal("2"),
                SizeClassCode.M, ServiceLevel.STANDARD, BigDecimal.ZERO);
        when(quotes.issue(input)).thenReturn(new QuoteSnapshot(id,
                new QuoteSnapshot.Money(new BigDecimal("475.00"), "LKR"),
                List.of(new FeeBreakdown.Line(PricingRule.BASE, new BigDecimal("475.00"))),
                "WP", "CP", "Colombo", "Kandy", new BigDecimal("2"), SizeClassCode.M,
                ServiceLevel.STANDARD, BigDecimal.ZERO, Instant.parse("2026-10-25T00:00:00Z"),
                Instant.parse("2026-10-25T00:15:00Z")));

        mvc.perform(post("/api/v1/pricing/quotes").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fromDistrict\":\" Colombo \",\"toDistrict\":\"Kandy\",\"weightKg\":2,\"sizeClass\":\"M\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quoteId").value(id.toString()))
                .andExpect(jsonPath("$.fee.amount").value(475.00))
                .andExpect(jsonPath("$.fee.currency").value("LKR"))
                .andExpect(jsonPath("$.breakdown[0].rule").value("BASE"))
                .andExpect(jsonPath("$.fromZone").value("WP"))
                .andExpect(jsonPath("$.toZone").value("CP"))
                .andExpect(jsonPath("$.serviceLevel").value("STANDARD"))
                .andExpect(jsonPath("$.expiresAt").value("2026-10-25T00:15:00Z"));
        verify(quotes).issue(input);
    }

    @Test
    void rejectsInvalidFieldsBeforeIssuingAQuote() throws Exception {
        mvc.perform(post("/api/v1/pricing/quotes").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fromDistrict\":\" \",\"toDistrict\":\"Kandy\",\"weightKg\":0,\"sizeClass\":\"M\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        verify(quotes, never()).issue(any());
    }

    @Test
    void rejectsUnservicedDistrictsWithThePublishedProblemCode() throws Exception {
        when(quotes.issue(any())).thenThrow(new BusinessRuleViolationException(
                "DISTRICT_NOT_SERVICED", "District is not served by an active zone"));

        mvc.perform(post("/api/v1/pricing/quotes").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fromDistrict\":\"Colombo\",\"toDistrict\":\"Kandy\",\"weightKg\":2,\"sizeClass\":\"M\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("DISTRICT_NOT_SERVICED"));
    }

    @Test
    void quoteLookupRequiresAuthentication() throws Exception {
        mvc.perform(get("/api/v1/pricing/quotes/" + UUID.randomUUID()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        verify(lookup, never()).get(any());
    }

    @Test
    void authenticatedLookupReturnsTheStoredSnapshot() throws Exception {
        UUID id = UUID.randomUUID();
        when(lookup.get(id)).thenReturn(new QuoteSnapshot(id,
                new QuoteSnapshot.Money(new BigDecimal("475.00"), "LKR"),
                List.of(new FeeBreakdown.Line(PricingRule.BASE, new BigDecimal("475.00"))),
                "WP", "CP", "Colombo", "Kandy", new BigDecimal("2"), SizeClassCode.M,
                ServiceLevel.STANDARD, BigDecimal.ZERO, Instant.parse("2026-10-25T00:00:00Z"),
                Instant.parse("2026-10-25T00:15:00Z")));

        mvc.perform(get("/api/v1/pricing/quotes/" + id).with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quoteId").value(id.toString()))
                .andExpect(jsonPath("$.fee.amount").value(475.00))
                .andExpect(jsonPath("$.fromDistrict").value("Colombo"));
        verify(lookup).get(id);
    }

    @Test
    void missingQuoteUsesTheSameProblemCodeAsAnExpiredQuote() throws Exception {
        UUID id = UUID.randomUUID();
        when(lookup.get(id)).thenThrow(new lk.swiftdrop.common.web.error.ResourceNotFoundException(
                "QUOTE_NOT_FOUND", "Quote " + id + " was not found"));

        mvc.perform(get("/api/v1/pricing/quotes/" + id).with(jwt()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("QUOTE_NOT_FOUND"));
    }
}
