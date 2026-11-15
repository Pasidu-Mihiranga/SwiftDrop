package lk.swiftdrop.pricing.api.v1;

import lk.swiftdrop.common.security.SwiftDropSecurityAutoConfiguration;
import lk.swiftdrop.common.web.SwiftDropWebAutoConfiguration;
import lk.swiftdrop.pricing.application.query.CoverageQueryService;
import lk.swiftdrop.pricing.domain.model.GeoPoint;
import lk.swiftdrop.pricing.domain.model.SpecialHandlingOption;
import lk.swiftdrop.pricing.domain.model.ZoneCode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Both endpoints are called anonymously, as the public quote form calls them. */
@WebMvcTest(CoverageController.class)
@ImportAutoConfiguration({SwiftDropWebAutoConfiguration.class, SwiftDropSecurityAutoConfiguration.class})
class CoverageControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private CoverageQueryService coverage;

    @Test
    void listsHandlingOptionsWithoutTheirFees() throws Exception {
        when(coverage.specialHandlingOptions()).thenReturn(List.of(
                new SpecialHandlingOption("FRAGILE", "Fragile", new BigDecimal("250.00"))));

        mvc.perform(get("/api/v1/pricing/special-handling"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].code").value("FRAGILE"))
                .andExpect(jsonPath("$[0].label").value("Fragile"))
                .andExpect(jsonPath("$[0].fee").doesNotExist());
    }

    @Test
    void resolvesAMapPointToItsDistrictAndZone() throws Exception {
        when(coverage.locate(GeoPoint.of("6.9344", "79.8428"))).thenReturn(
                Optional.of(new CoverageQueryService.LocatedPoint("Colombo", ZoneCode.of("WP"))));

        mvc.perform(get("/api/v1/pricing/locate").param("latitude", "6.9344").param("longitude", "79.8428"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.district").value("Colombo"))
                .andExpect(jsonPath("$.zoneCode").value("WP"));
    }

    @Test
    void aPointOutsideEveryDistrictIsAnsweredWithAProblem() throws Exception {
        when(coverage.locate(any())).thenReturn(Optional.empty());

        mvc.perform(get("/api/v1/pricing/locate").param("latitude", "6.9").param("longitude", "79.5"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("OUTSIDE_COVERAGE"));
    }

    @Test
    void refusesCoordinatesOffTheGlobe() throws Exception {
        mvc.perform(get("/api/v1/pricing/locate").param("latitude", "91").param("longitude", "80"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_COORDINATES"));
        verify(coverage, never()).locate(any());
    }

    @Test
    void refusesARequestWithoutBothCoordinates() throws Exception {
        mvc.perform(get("/api/v1/pricing/locate").param("latitude", "6.9"))
                .andExpect(status().isBadRequest());
    }
}
