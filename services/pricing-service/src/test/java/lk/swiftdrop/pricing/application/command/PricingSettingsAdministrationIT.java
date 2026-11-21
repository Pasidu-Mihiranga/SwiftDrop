package lk.swiftdrop.pricing.application.command;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The V16 configuration maintained through the admin API against real Postgres, where the V16
 * constraints and V17's new column are applied by Flyway exactly as in production.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class PricingSettingsAdministrationIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16.15-alpine");
    @Container
    @ServiceConnection(name = "redis")
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7.4.11-alpine").withExposedPorts(6379);

    @Autowired
    private MockMvc mvc;

    private static RequestPostProcessor admin() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    private static String settings(String fuel, String cutoff, String from) {
        return "{\"fuelSurchargePct\":" + fuel + ",\"taxPct\":0.18,\"minimumCharge\":350,\"sameDayCutoff\":"
                + (cutoff == null ? "null" : "\"" + cutoff + "\"") + ",\"effectiveFrom\":\"" + from + "\"}";
    }

    @Test
    void theSeededSettingsAreTheFirstVersionAndANewOneClosesThem() throws Exception {
        mvc.perform(get("/api/v1/admin/pricing/settings").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.effectiveFrom == '2026-01-01')].fuelSurchargePct", hasItem(0.05)))
                .andExpect(jsonPath("$[?(@.effectiveFrom == '2026-01-01')].sameDayCutoff", hasItem(nullValue())));

        mvc.perform(post("/api/v1/admin/pricing/settings").with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content(settings("0.06", "14:00", "2031-03-01")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sameDayCutoff").value("14:00:00"));

        mvc.perform(post("/api/v1/admin/pricing/settings").with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content(settings("0.07", null, "2031-03-01")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRICING_SETTINGS_PERIOD_CONFLICT"));
        mvc.perform(post("/api/v1/admin/pricing/settings").with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content(settings("0.07", null, "2020-01-01")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("EFFECTIVE_DATE_IN_PAST"));

        mvc.perform(get("/api/v1/admin/pricing/settings").with(admin()))
                .andExpect(jsonPath("$[0].effectiveFrom").value("2031-03-01"))
                .andExpect(jsonPath("$[?(@.effectiveFrom == '2026-01-01')].effectiveTo", hasItem("2031-03-01")));
    }

    @Test
    void aNewBandSetClosesEveryBandInForce() throws Exception {
        mvc.perform(get("/api/v1/admin/pricing/distance-bands").with(admin()))
                .andExpect(jsonPath("$[?(@.effectiveFrom == '2026-01-01')]", hasSize(5)));

        mvc.perform(post("/api/v1/admin/pricing/distance-bands").with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"effectiveFrom\":\"2031-02-01\",\"bands\":["
                                + "{\"minKm\":0,\"maxKm\":50,\"lineHaulFee\":0,\"standardTransitDays\":1,"
                                + "\"expressTransitDays\":1},"
                                + "{\"minKm\":50,\"maxKm\":null,\"lineHaulFee\":400,\"standardTransitDays\":3,"
                                + "\"expressTransitDays\":2}]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$", hasSize(2)));

        mvc.perform(get("/api/v1/admin/pricing/distance-bands").with(admin()))
                .andExpect(jsonPath("$[0].effectiveFrom").value("2031-02-01"))
                .andExpect(jsonPath("$[?(@.effectiveFrom == '2026-01-01' && @.effectiveTo == '2031-02-01')]",
                        hasSize(5)));

        mvc.perform(post("/api/v1/admin/pricing/distance-bands").with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"effectiveFrom\":\"2031-06-01\",\"bands\":["
                                + "{\"minKm\":10,\"maxKm\":null,\"lineHaulFee\":0,\"standardTransitDays\":1,"
                                + "\"expressTransitDays\":1}]}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INVALID_DISTANCE_BANDS"));
    }

    @Test
    void handlingOptionsCanBeAddedRetiredAndAreHiddenFromCustomersOnceRetired() throws Exception {
        mvc.perform(post("/api/v1/admin/pricing/special-handling").with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"oversize\",\"label\":\"Oversize item\",\"fee\":500,\"sortOrder\":50}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("OVERSIZE"));
        mvc.perform(get("/api/v1/pricing/special-handling"))
                .andExpect(jsonPath("$[*].code", hasItem("OVERSIZE")));

        mvc.perform(post("/api/v1/admin/pricing/special-handling").with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"OVERSIZE\",\"label\":\"Again\",\"fee\":1,\"sortOrder\":1}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SPECIAL_HANDLING_EXISTS"));

        mvc.perform(put("/api/v1/admin/pricing/special-handling/OVERSIZE").with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"label\":\"Oversize item\",\"fee\":500,\"active\":false,\"sortOrder\":50}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/pricing/special-handling"))
                .andExpect(jsonPath("$[?(@.code == 'OVERSIZE')]", hasSize(0)));
        mvc.perform(get("/api/v1/admin/pricing/special-handling").with(admin()))
                .andExpect(jsonPath("$[?(@.code == 'OVERSIZE')].active", hasItem(false)));
    }

    @Test
    void aVehiclesLimitsCanBeChanged() throws Exception {
        mvc.perform(put("/api/v1/admin/pricing/vehicles/BIKE").with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"label\":\"Motorbike\",\"maxWeightKg\":12,\"maxDistanceKm\":45,"
                                + "\"rateMultiplier\":1.0,\"active\":true}"))
                .andExpect(status().isOk());

        mvc.perform(get("/api/v1/admin/pricing/vehicles").with(admin()))
                .andExpect(jsonPath("$[?(@.code == 'BIKE')].label", hasItem("Motorbike")))
                .andExpect(jsonPath("$[?(@.code == 'BIKE')].maxDistanceKm", hasItem(45.0)));
    }
}
