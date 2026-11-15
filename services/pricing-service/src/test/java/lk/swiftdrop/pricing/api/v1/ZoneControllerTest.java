package lk.swiftdrop.pricing.api.v1;

import lk.swiftdrop.common.security.SwiftDropSecurityAutoConfiguration;
import lk.swiftdrop.common.web.SwiftDropWebAutoConfiguration;
import lk.swiftdrop.pricing.application.query.ZoneQueryService;
import lk.swiftdrop.pricing.domain.model.Zone;
import lk.swiftdrop.pricing.domain.model.ZoneCode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ZoneController.class)
@ImportAutoConfiguration({SwiftDropWebAutoConfiguration.class, SwiftDropSecurityAutoConfiguration.class})
class ZoneControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private ZoneQueryService zones;

    @Test
    void publishesActiveCoverageToAnonymousVisitors() throws Exception {
        when(zones.activeCoverage()).thenReturn(List.of(
                new Zone(UUID.randomUUID(), ZoneCode.of("CP"), "Central", true, List.of("Kandy", "Matale"))));

        mvc.perform(get("/api/v1/pricing/zones"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("CP"))
                .andExpect(jsonPath("$[0].name").value("Central"))
                .andExpect(jsonPath("$[0].districts[0]").value("Kandy"))
                .andExpect(jsonPath("$[0].active").value(true));
    }

    @Test
    void doesNotLeakTheSurrogateZoneIdToThePublic() throws Exception {
        when(zones.activeCoverage()).thenReturn(List.of(
                new Zone(UUID.randomUUID(), ZoneCode.of("WP"), "Western", true, List.of("Colombo"))));

        mvc.perform(get("/api/v1/pricing/zones"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").doesNotExist());
    }

    @Test
    void returnsAnEmptyArrayWhenNothingIsServicedYet() throws Exception {
        when(zones.activeCoverage()).thenReturn(List.of());

        mvc.perform(get("/api/v1/pricing/zones"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }
}
