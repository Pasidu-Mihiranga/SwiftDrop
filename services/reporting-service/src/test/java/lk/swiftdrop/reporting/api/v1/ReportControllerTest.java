package lk.swiftdrop.reporting.api.v1;

import lk.swiftdrop.common.security.KeycloakRealmRoleConverter;
import lk.swiftdrop.common.security.SwiftDropSecurityAutoConfiguration;
import lk.swiftdrop.common.web.SwiftDropWebAutoConfiguration;
import lk.swiftdrop.reporting.application.query.ReportQueryService;
import lk.swiftdrop.reporting.domain.model.CourierPerformance;
import lk.swiftdrop.reporting.domain.model.DailyCounts;
import lk.swiftdrop.reporting.domain.model.DeliveryCounts;
import lk.swiftdrop.reporting.domain.model.ReportPeriod;
import lk.swiftdrop.reporting.domain.model.ZoneCounts;
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

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReportController.class)
@ImportAutoConfiguration({SwiftDropWebAutoConfiguration.class, SwiftDropSecurityAutoConfiguration.class})
class ReportControllerTest {

    private static final LocalDate OCT_1 = LocalDate.of(2026, 10, 1);

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private ReportQueryService reports;

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "DISPATCHER"})
    void operationsStaffGetTheDailySeriesWithTotals(String role) throws Exception {
        when(reports.daily(new ReportPeriod(OCT_1, OCT_1.plusDays(1)), Optional.empty())).thenReturn(List.of(
                new DailyCounts(OCT_1, new DeliveryCounts(4, 3, 1, 0)),
                new DailyCounts(OCT_1.plusDays(1), DeliveryCounts.ZERO)));

        mvc.perform(get("/api/v1/reports/daily").param("from", "2026-10-01").param("to", "2026-10-02").with(as(role)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.days.length()").value(2))
                .andExpect(jsonPath("$.days[0].date").value("2026-10-01"))
                .andExpect(jsonPath("$.days[0].delivered").value(3))
                .andExpect(jsonPath("$.days[1].created").value(0))
                .andExpect(jsonPath("$.totals.created").value(4))
                .andExpect(jsonPath("$.totals.successRate").value(0.75));
    }

    @Test
    void successRateCanBeNarrowedToAZone() throws Exception {
        when(reports.totals(new ReportPeriod(OCT_1, OCT_1), Optional.of("CP-KDY")))
                .thenReturn(new DeliveryCounts(10, 9, 1, 0));

        mvc.perform(get("/api/v1/reports/success-rate").param("from", "2026-10-01").param("to", "2026-10-01")
                        .param("zone", "CP-KDY").with(as("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.zone").value("CP-KDY"))
                .andExpect(jsonPath("$.delivered").value(9))
                .andExpect(jsonPath("$.failed").value(1))
                .andExpect(jsonPath("$.successRate").value(0.9));
    }

    @Test
    void successRateIsNullWhenNothingWasAttempted() throws Exception {
        when(reports.totals(any(), any())).thenReturn(DeliveryCounts.ZERO);

        mvc.perform(get("/api/v1/reports/success-rate").param("from", "2026-10-01").param("to", "2026-10-01")
                        .with(as("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.successRate").doesNotExist());
    }

    @Test
    void zonesAndCouriersAreListed() throws Exception {
        ReportPeriod period = new ReportPeriod(OCT_1, OCT_1.plusDays(6));
        when(reports.zones(period)).thenReturn(List.of(new ZoneCounts("CP-KDY", new DeliveryCounts(5, 4, 0, 1))));
        when(reports.couriers(period)).thenReturn(List.of(new CourierPerformance("courier-1", 8, 2)));

        mvc.perform(get("/api/v1/reports/zones").param("from", "2026-10-01").param("to", "2026-10-07").with(as("DISPATCHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].zoneCode").value("CP-KDY"))
                .andExpect(jsonPath("$[0].cancelled").value(1))
                .andExpect(jsonPath("$[0].successRate").value(1.0));
        mvc.perform(get("/api/v1/reports/couriers").param("from", "2026-10-01").param("to", "2026-10-07").with(as("DISPATCHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].courierId").value("courier-1"))
                .andExpect(jsonPath("$[0].successRate").value(0.8));
    }

    @ParameterizedTest
    @ValueSource(strings = {"CUSTOMER", "COURIER", "MERCHANT", "MERCHANT_API"})
    void otherRolesAreForbidden(String role) throws Exception {
        mvc.perform(get("/api/v1/reports/daily").param("from", "2026-10-01").param("to", "2026-10-02").with(as(role)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void anonymousCallersMustSignIn() throws Exception {
        mvc.perform(get("/api/v1/reports/couriers").param("from", "2026-10-01").param("to", "2026-10-02"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void reversedPeriodIsABadRequest() throws Exception {
        mvc.perform(get("/api/v1/reports/daily").param("from", "2026-10-05").param("to", "2026-10-01").with(as("ADMIN")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REPORT_PERIOD"));
    }

    @Test
    void periodLongerThanAYearIsABadRequest() throws Exception {
        mvc.perform(get("/api/v1/reports/zones").param("from", "2025-01-01").param("to", "2026-06-01").with(as("ADMIN")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REPORT_PERIOD"));
    }

    @Test
    void malformedInputIsABadRequest() throws Exception {
        mvc.perform(get("/api/v1/reports/daily").param("from", "yesterday").param("to", "2026-10-01").with(as("ADMIN")))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/reports/daily").param("to", "2026-10-01").with(as("ADMIN")))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/reports/daily").param("from", "2026-10-01").param("to", "2026-10-01")
                        .param("zone", "drop table;").with(as("ADMIN")))
                .andExpect(status().isBadRequest());
    }

    private static RequestPostProcessor as(String role) {
        return jwt().jwt(token -> token.subject("user-1").claim("realm_access", Map.of("roles", List.of(role))))
                .authorities(new KeycloakRealmRoleConverter());
    }
}
