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

import java.time.LocalDate;
import java.util.UUID;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The admin screens' APIs against real Postgres, where the period constraints actually bite. */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class PricingConfigurationAdministrationIT {

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

    private static String charge(String fee, String from) {
        return "{\"flatFee\":" + fee + ",\"effectiveFrom\":\"" + from + "\"}";
    }

    @Test
    void aRemoteChargeCanBeReplacedLaterButNotOnTheSameDayOrInThePast() throws Exception {
        String path = "/api/v1/admin/pricing/remote-areas/Mannar";
        LocalDate start = LocalDate.of(2031, 1, 1);

        mvc.perform(put(path).with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content(charge("100.00", start.toString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.effectiveFrom").value(start.toString()));

        mvc.perform(put(path).with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content(charge("120.00", start.toString())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REMOTE_AREA_PERIOD_CONFLICT"));
        mvc.perform(put(path).with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content(charge("120.00", start.minusDays(30).toString())))
                .andExpect(status().isConflict());

        mvc.perform(put(path).with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content(charge("120.00", start.plusDays(1).toString())))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/admin/pricing/remote-areas").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.district == 'Mannar' && @.flatFee == 100.00)].effectiveTo",
                        hasItem(start.plusDays(1).toString())));
    }

    @Test
    void aMisspeltDistrictIsRefused() throws Exception {
        mvc.perform(put("/api/v1/admin/pricing/remote-areas/Manarr").with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content(charge("100.00", "2031-01-01")))
                .andExpect(status().isNotFound());
    }

    @Test
    void agreementsAreListedWithTheirPeriodsAndCannotBeRepublishedOnTheSameDay() throws Exception {
        UUID merchant = UUID.randomUUID();
        String path = "/api/v1/admin/pricing/merchants/" + merchant + "/agreements";
        String today = LocalDate.now().minusDays(1).toString();
        String terms = """
                {"discountPct":0.1000,"effectiveFrom":"%s",
                 "laneOverrides":[{"fromZone":"WP","toZone":"CP","flatBaseFee":300.00}]}
                """.formatted(today);

        mvc.perform(put(path).with(admin()).contentType(MediaType.APPLICATION_JSON).content(terms))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.effectiveFrom").value(today));
        mvc.perform(put(path).with(admin()).contentType(MediaType.APPLICATION_JSON).content(terms))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("AGREEMENT_PERIOD_CONFLICT"));

        mvc.perform(get("/api/v1/admin/pricing/merchants").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].merchantId", hasItem(merchant.toString())));
    }

    @Test
    void refusesALaneInAnUnknownZoneAndALaneThatChangesNothing() throws Exception {
        String path = "/api/v1/admin/pricing/merchants/" + UUID.randomUUID() + "/agreements";

        mvc.perform(put(path).with(admin()).contentType(MediaType.APPLICATION_JSON).content("""
                        {"effectiveFrom":"2031-01-01",
                         "laneOverrides":[{"fromZone":"WP","toZone":"ZZ","discountPct":0.1}]}
                        """))
                .andExpect(status().isNotFound());
        mvc.perform(put(path).with(admin()).contentType(MediaType.APPLICATION_JSON).content("""
                        {"effectiveFrom":"2031-01-01",
                         "laneOverrides":[{"fromZone":"WP","toZone":"CP"}]}
                        """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void weightBandsSurviveTheRoundTripThroughEveryRead() throws Exception {
        String seeded = mvc.perform(get("/api/v1/admin/pricing/rate-cards?fromZone=NP&toZone=SP").with(admin()))
                .andReturn().getResponse().getContentAsString();
        String seededId = com.jayway.jsonpath.JsonPath.read(seeded, "$.content[0].id");
        mvc.perform(put("/api/v1/admin/pricing/rate-cards/" + seededId + "/closure").with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"effectiveTo\":\"2031-01-01\"}"))
                .andExpect(status().isOk());

        String created = mvc.perform(post("/api/v1/admin/pricing/rate-cards").with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"fromZone":"NP","toZone":"SP","baseFee":500.00,"perKgFee":80.00,
                                 "expressMultiplier":1.500,"codFeePct":0.0150,
                                 "effectiveFrom":"2031-01-01",
                                 "tiers":[{"upToKg":5.00,"perKgFee":80.00},{"perKgFee":60.00}]}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tiers.length()").value(2))
                .andReturn().getResponse().getContentAsString();
        String id = com.jayway.jsonpath.JsonPath.read(created, "$.id");

        mvc.perform(get("/api/v1/admin/pricing/rate-cards/" + id).with(admin()))
                .andExpect(jsonPath("$.tiers[0].upToKg").value(5.00))
                .andExpect(jsonPath("$.tiers[1].perKgFee").value(60.00));
        mvc.perform(get("/api/v1/admin/pricing/rate-cards?fromZone=NP&toZone=SP&activeOn=2031-02-01").with(admin()))
                .andExpect(jsonPath("$.content[0].tiers.length()").value(2));
    }
}
