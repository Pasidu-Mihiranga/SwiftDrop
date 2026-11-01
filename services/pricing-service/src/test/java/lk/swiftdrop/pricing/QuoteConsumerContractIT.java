package lk.swiftdrop.pricing;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * What parcel-service can rely on, written the way it will use the API: a customer gets a quote,
 * and at booking the service looks it up over HTTP with that customer's token.
 *
 * <p>This is the pricing side of "a 16-minute-old quote is rejected when creating a parcel". The
 * booking side belongs to parcel-service, which should treat the 404 here as an expired quote.
 */
@Testcontainers
@SpringBootTest(properties = "swiftdrop.pricing.quote-ttl=PT15M")
@AutoConfigureMockMvc
class QuoteConsumerContractIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16.15-alpine");
    @Container
    @ServiceConnection(name = "redis")
    static final GenericContainer<?> REDIS =
            new GenericContainer<>("redis:7.4.11-alpine").withExposedPorts(6379);

    private static final Instant ISSUED_AT = Instant.parse("2026-06-01T09:00:00Z");
    private static final String QUOTES = "/api/v1/pricing/quotes";

    @MockitoBean
    private Clock clock;

    @Autowired
    private MockMvc mvc;

    @BeforeEach
    void freezeTheClock() {
        when(clock.getZone()).thenReturn(ZoneId.of("Asia/Colombo"));
        when(clock.instant()).thenReturn(ISSUED_AT);
    }

    private String issue(String body) throws Exception {
        String response = mvc.perform(post(QUOTES).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.quoteId");
    }

    private String issueColomboToKandy() throws Exception {
        return issue("""
                {"fromDistrict":"Colombo","toDistrict":"Kandy","weightKg":2,"sizeClass":"M"}
                """);
    }

    @Test
    void aBookingCanReadEveryFieldItNeedsWithTheCustomersToken() throws Exception {
        String quoteId = issueColomboToKandy();

        mvc.perform(get(QUOTES + "/" + quoteId).with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quoteId").value(quoteId))
                .andExpect(jsonPath("$.fee.amount").isNumber())
                .andExpect(jsonPath("$.fee.currency").value("LKR"))
                .andExpect(jsonPath("$.breakdown").isArray())
                .andExpect(jsonPath("$.fromDistrict").value("Colombo"))
                .andExpect(jsonPath("$.toDistrict").value("Kandy"))
                .andExpect(jsonPath("$.fromZone").value("WP"))
                .andExpect(jsonPath("$.toZone").value("CP"))
                .andExpect(jsonPath("$.weightKg").value(2))
                .andExpect(jsonPath("$.sizeClass").value("M"))
                .andExpect(jsonPath("$.serviceLevel").value("STANDARD"))
                .andExpect(jsonPath("$.issuedAt").exists())
                .andExpect(jsonPath("$.expiresAt").exists());
    }

    @Test
    void theLookupNeedsAToken() throws Exception {
        mvc.perform(get(QUOTES + "/" + issueColomboToKandy())).andExpect(status().isUnauthorized());
    }

    @Test
    void aSixteenMinuteOldQuoteIsAnsweredAs404() throws Exception {
        String quoteId = issueColomboToKandy();

        when(clock.instant()).thenReturn(ISSUED_AT.plus(Duration.ofMinutes(16)));

        mvc.perform(get(QUOTES + "/" + quoteId).with(jwt()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("QUOTE_NOT_FOUND"));
    }

    @Test
    void aBookingMustStoreTheChargedSizeClassNotTheOneSelected() throws Exception {
        String quoteId = issue("""
                {"fromDistrict":"Colombo","toDistrict":"Kandy","weightKg":3,"sizeClass":"S",
                 "lengthCm":60,"widthCm":40,"heightCm":30}
                """);

        mvc.perform(get(QUOTES + "/" + quoteId).with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sizeClass").value("L"));
    }
}
