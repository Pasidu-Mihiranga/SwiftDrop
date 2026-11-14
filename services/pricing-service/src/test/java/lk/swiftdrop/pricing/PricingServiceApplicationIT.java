package lk.swiftdrop.pricing;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Boots the whole service against real infrastructure: Flyway migrations, JPA mapping, security, actuator. */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class PricingServiceApplicationIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16.15-alpine");
    @Container
    @ServiceConnection(name = "redis")
    static final org.testcontainers.containers.GenericContainer<?> REDIS =
            new org.testcontainers.containers.GenericContainer<>("redis:7.4.11-alpine").withExposedPorts(6379);

    @Autowired
    private MockMvc mvc;
    @Autowired
    private StringRedisTemplate redis;
    @Autowired
    private lk.swiftdrop.pricing.application.port.out.RemoteAreaSurchargeRepository remoteAreas;
    @Autowired
    private lk.swiftdrop.pricing.application.port.out.MerchantAgreementRepository merchantAgreements;
    @Autowired
    private ObjectMapper mapper;

    @Test
    void startsAndReportsHealthy() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void protectsBusinessEndpoints() throws Exception {
        mvc.perform(get("/api/v1/pricing/meta/whoami")).andExpect(status().isUnauthorized());
    }

    @Test
    void issuesAPublicQuoteAndStoresTheReturnedSnapshotWithExpiry() throws Exception {
        String response = mvc.perform(post("/api/v1/pricing/quotes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fromDistrict\":\"Colombo\",\"toDistrict\":\"Kandy\",\"weightKg\":2,\"sizeClass\":\"M\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fee.currency").value("LKR"))
                .andExpect(jsonPath("$.fromZone").value("WP"))
                .andExpect(jsonPath("$.toZone").value("CP"))
                .andReturn().getResponse().getContentAsString();

        String key = "pricing:quote:" + mapper.readTree(response).get("quoteId").asText();
        // Stored inside a versioned envelope, so a quote issued by the previous release can
        // still be read after a deploy. The quote itself is what was sent to the customer.
        var stored = mapper.readTree(redis.opsForValue().get(key));
        assertThat(stored.get("schemaVersion").asInt()).isEqualTo(1);
        // The stored shape and the published shape are deliberately not the same object any
        // more, so compare what a customer was told rather than the whole document.
        var quote = stored.get("quote");
        for (String field : new String[] {"quoteId", "fee", "breakdown", "fromZone", "toZone",
                "weightKg", "sizeClass", "serviceLevel", "issuedAt", "expiresAt"}) {
            assertThat(quote.get(field)).as(field).isEqualTo(mapper.readTree(response).get(field));
        }
        assertThat(redis.getExpire(key, TimeUnit.SECONDS)).isBetween(1L, 900L);

        String quoteId = mapper.readTree(response).get("quoteId").asText();
        mvc.perform(get("/api/v1/pricing/quotes/" + quoteId))
                .andExpect(status().isUnauthorized());
        String lookedUp = mvc.perform(get("/api/v1/pricing/quotes/" + quoteId + "?weightKg=99&fee=0")
                        .with(jwt()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(mapper.readTree(lookedUp)).isEqualTo(mapper.readTree(response));

        redis.delete(key);
        mvc.perform(get("/api/v1/pricing/quotes/" + quoteId).with(jwt()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("QUOTE_NOT_FOUND"));
    }

    @Test
    void chargesABulkyParcelOnTheSpaceItTakesUpAndMovesItIntoTheClassItFits() throws Exception {
        // 3 kg in a 60x40x30 box: 72000 cubic centimetres over the seeded 5000 divisor is
        // 14.4 kg of van. The customer selected S, which that parcel does not come close to.
        String response = mvc.perform(post("/api/v1/pricing/quotes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fromDistrict":"Colombo","toDistrict":"Matara","weightKg":3,
                                 "sizeClass":"S","lengthCm":60,"widthCm":40,"heightCm":30}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.weightKg").value(3))
                .andExpect(jsonPath("$.chargeableWeightKg").value(14.40))
                .andExpect(jsonPath("$.volumetricWeightKg").value(14.40))
                .andExpect(jsonPath("$.weightBasis").value("VOLUMETRIC"))
                .andExpect(jsonPath("$.sizeClass").value("L"))
                .andExpect(jsonPath("$.declaredSizeClass").value("S"))
                .andReturn().getResponse().getContentAsString();

        // Whatever the rules did, the lines still add up to what is charged.
        var quote = mapper.readTree(response);
        var lines = quote.get("breakdown").valueStream()
                .map(line -> new java.math.BigDecimal(line.get("amount").asString()))
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
        assertThat(lines).isEqualByComparingTo(new java.math.BigDecimal(quote.get("fee").get("amount").asString()));
    }

    @Test
    void anUnmeasuredParcelIsQuotedExactlyAsItWasBeforeVolumetricWeightExisted() throws Exception {
        mvc.perform(post("/api/v1/pricing/quotes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fromDistrict\":\"Colombo\",\"toDistrict\":\"Kandy\",\"weightKg\":2,\"sizeClass\":\"M\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sizeClass").value("M"))
                .andExpect(jsonPath("$.weightBasis").value("ACTUAL"))
                .andExpect(jsonPath("$.chargeableWeightKg").value(2))
                // Nothing to report, so nothing is sent: the shape stays as callers know it.
                .andExpect(jsonPath("$.volumetricWeightKg").doesNotExist())
                .andExpect(jsonPath("$.declaredSizeClass").doesNotExist());
    }

    @Test
    void refusesTwoSidesOfAParcelRatherThanGuessingTheThird() throws Exception {
        mvc.perform(post("/api/v1/pricing/quotes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fromDistrict":"Colombo","toDistrict":"Kandy","weightKg":2,
                                 "sizeClass":"M","lengthCm":60,"widthCm":40}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void chargesCoverFromTheBandTheDeclaredValueFallsIn() throws Exception {
        // 30000 is in the second seeded band: one per cent is 300, which beats its flat 100.
        mvc.perform(post("/api/v1/pricing/quotes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fromDistrict":"Colombo","toDistrict":"Kandy","weightKg":2,
                                 "sizeClass":"M","declaredValue":30000}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.declaredValue").value(30000))
                .andExpect(jsonPath("$.breakdown[?(@.rule=='INSURANCE')].amount").value(300.00));
    }

    @Test
    void chargesNoCoverForAParcelSentWithoutADeclaredValue() throws Exception {
        mvc.perform(post("/api/v1/pricing/quotes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fromDistrict\":\"Colombo\",\"toDistrict\":\"Kandy\",\"weightKg\":2,\"sizeClass\":\"M\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.declaredValue").doesNotExist())
                .andExpect(jsonPath("$.breakdown[?(@.rule=='INSURANCE')]").isEmpty());
    }

    @Test
    void chargesBothEndsOfAJourneyBetweenTwoHardToReachDistricts() throws Exception {
        remoteAreas.publish("Jaffna", new java.math.BigDecimal("250.00"), java.time.LocalDate.of(2026, 1, 1));
        remoteAreas.publish("Mannar", new java.math.BigDecimal("400.00"), java.time.LocalDate.of(2026, 1, 1));

        mvc.perform(post("/api/v1/pricing/quotes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fromDistrict\":\"Jaffna\",\"toDistrict\":\"Mannar\",\"weightKg\":2,\"sizeClass\":\"M\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.breakdown[?(@.rule=='REMOTE_AREA')].amount").value(650.00));
    }

    @Test
    void appliesAMerchantsNegotiatedRateOnlyWhenItsTokenIsPresented() throws Exception {
        java.util.UUID merchant = java.util.UUID.randomUUID();
        merchantAgreements.publish(new lk.swiftdrop.pricing.domain.model.MerchantAgreement(
                        null, merchant, new java.math.BigDecimal("0.2000"), java.math.BigDecimal.ZERO,
                        false, false, false, java.util.List.of()),
                java.time.LocalDate.of(2026, 1, 1));
        String body = "{\"fromDistrict\":\"Colombo\",\"toDistrict\":\"Kandy\",\"weightKg\":2,\"sizeClass\":\"M\"}";

        // The same request without a token is the counter price, and the path stays anonymous.
        String anonymous = mvc.perform(post("/api/v1/pricing/quotes")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.agreementId").doesNotExist())
                .andExpect(jsonPath("$.breakdown[?(@.rule=='MERCHANT_DISCOUNT')]").isEmpty())
                .andReturn().getResponse().getContentAsString();

        String negotiated = mvc.perform(post("/api/v1/pricing/quotes")
                        .contentType(MediaType.APPLICATION_JSON).content(body)
                        .with(jwt().jwt(token -> token.claim("merchant_id", merchant.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.agreementId").exists())
                .andReturn().getResponse().getContentAsString();

        var counter = new java.math.BigDecimal(mapper.readTree(anonymous).get("fee").get("amount").asString());
        var discounted = new java.math.BigDecimal(mapper.readTree(negotiated).get("fee").get("amount").asString());
        assertThat(discounted).isLessThan(counter);
        // The discount is a line like any other, so the breakdown still reconciles.
        var lines = mapper.readTree(negotiated).get("breakdown").valueStream()
                .map(line -> new java.math.BigDecimal(line.get("amount").asString()))
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
        assertThat(lines).isEqualByComparingTo(discounted);
    }

    @Test
    void ignoresAMalformedMerchantClaimRatherThanRefusingTheQuote() throws Exception {
        mvc.perform(post("/api/v1/pricing/quotes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fromDistrict\":\"Colombo\",\"toDistrict\":\"Kandy\",\"weightKg\":2,\"sizeClass\":\"M\"}")
                        .with(jwt().jwt(token -> token.claim("merchant_id", "not-a-uuid"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.agreementId").doesNotExist());
    }

    @Test
    void pricesASameDayJobOnTheRoadTravelledRatherThanTheZone() throws Exception {
        String response = mvc.perform(post("/api/v1/pricing/quotes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fromDistrict":"Colombo","toDistrict":"Gampaha","weightKg":2,
                                 "sizeClass":"M","serviceLevel":"SAME_DAY"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.serviceLevel").value("SAME_DAY"))
                // Priced on distance, so there is no zone base charge at all.
                .andExpect(jsonPath("$.breakdown[?(@.rule=='DISTANCE')]").isNotEmpty())
                .andExpect(jsonPath("$.breakdown[?(@.rule=='BASE')]").isEmpty())
                .andExpect(jsonPath("$.distanceKm").isNumber())
                // No routing engine is deployed, so the figure is openly an estimate.
                .andExpect(jsonPath("$.distanceSource").value("ESTIMATED"))
                .andReturn().getResponse().getContentAsString();

        var quote = mapper.readTree(response);
        var lines = quote.get("breakdown").valueStream()
                .map(line -> new java.math.BigDecimal(line.get("amount").asString()))
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
        assertThat(lines).isEqualByComparingTo(
                new java.math.BigDecimal(quote.get("fee").get("amount").asString()));
    }

    @Test
    void usesTheExactPointsForASameDayJobWhenTheCallerGivesThem() throws Exception {
        // Two points a few hundred metres apart in Colombo, which the district centroid alone
        // could never tell apart.
        String nearby = mvc.perform(post("/api/v1/pricing/quotes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fromDistrict":"Colombo","toDistrict":"Colombo","weightKg":2,
                                 "sizeClass":"M","serviceLevel":"SAME_DAY",
                                 "fromLatitude":6.927079,"fromLongitude":79.861244,
                                 "toLatitude":6.934000,"toLongitude":79.857000}"""))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // Under the three kilometre minimum, so it is billed at the minimum rather than free.
        assertThat(new java.math.BigDecimal(mapper.readTree(nearby).get("distanceKm").asString()))
                .isLessThan(new java.math.BigDecimal("3"));
        assertThat(new java.math.BigDecimal(
                mapper.readTree(nearby).get("breakdown").valueStream()
                        .filter(line -> "DISTANCE".equals(line.get("rule").asString()))
                        .findFirst().orElseThrow().get("amount").asString()))
                .isEqualByComparingTo("465.00");
    }

    @Test
    void refusesHalfACoordinateRatherThanGuessingTheRest() throws Exception {
        mvc.perform(post("/api/v1/pricing/quotes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fromDistrict":"Colombo","toDistrict":"Gampaha","weightKg":2,
                                 "sizeClass":"M","serviceLevel":"SAME_DAY","fromLatitude":6.9}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }
}
