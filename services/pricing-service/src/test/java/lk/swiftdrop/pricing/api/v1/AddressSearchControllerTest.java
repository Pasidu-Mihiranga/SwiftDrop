package lk.swiftdrop.pricing.api.v1;

import lk.swiftdrop.common.security.SwiftDropSecurityAutoConfiguration;
import lk.swiftdrop.common.web.SwiftDropWebAutoConfiguration;
import lk.swiftdrop.common.web.error.BadRequestException;
import lk.swiftdrop.pricing.application.query.AddressSearchService;
import lk.swiftdrop.pricing.application.query.AddressSearchService.Suggestion;
import lk.swiftdrop.pricing.application.query.AddressSearchUnavailableException;
import lk.swiftdrop.pricing.domain.model.GeoPoint;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AddressSearchController.class)
@ImportAutoConfiguration({SwiftDropWebAutoConfiguration.class, SwiftDropSecurityAutoConfiguration.class})
@TestPropertySource(properties = "swiftdrop.security.public-paths=/api/v1/pricing/address-search")
class AddressSearchControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private AddressSearchService addresses;

    @Test
    void answersAnonymousVisitorsWithSuggestionsThatNoCacheKeeps() throws Exception {
        when(addresses.search("Galle Road")).thenReturn(List.of(
                new Suggestion("45, Galle Road, Colombo 03", GeoPoint.of("6.91", "79.85"), "Colombo", "WP")));

        mvc.perform(get("/api/v1/pricing/address-search").param("q", "Galle Road"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$[0].label").value("45, Galle Road, Colombo 03"))
                .andExpect(jsonPath("$[0].latitude").value(6.91))
                .andExpect(jsonPath("$[0].district").value("Colombo"))
                .andExpect(jsonPath("$[0].zone").value("WP"));
    }

    @Test
    void answers503WhenSearchIsOffAnd400ForABadQuery() throws Exception {
        when(addresses.search("Kandy")).thenThrow(new AddressSearchUnavailableException("off"));
        when(addresses.search("ab")).thenThrow(new BadRequestException("INVALID_ADDRESS_QUERY", "too short"));

        mvc.perform(get("/api/v1/pricing/address-search").param("q", "Kandy"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("ADDRESS_SEARCH_UNAVAILABLE"));
        mvc.perform(get("/api/v1/pricing/address-search").param("q", "ab"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ADDRESS_QUERY"));
        mvc.perform(get("/api/v1/pricing/address-search"))
                .andExpect(status().isBadRequest());
    }
}
