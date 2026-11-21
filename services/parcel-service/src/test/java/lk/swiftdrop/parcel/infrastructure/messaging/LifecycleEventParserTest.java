package lk.swiftdrop.parcel.infrastructure.messaging;

import lk.swiftdrop.events.EventEnvelope;
import lk.swiftdrop.events.common.Types.Actor;
import lk.swiftdrop.events.common.Types.ActorRole;
import lk.swiftdrop.events.merchant.MerchantLifecycleChanged;
import lk.swiftdrop.parcel.domain.model.MerchantStanding;
import lk.swiftdrop.parcel.domain.model.MerchantStatus;
import lk.swiftdrop.parcel.infrastructure.messaging.LifecycleEventParser.LifecycleEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LifecycleEventParserTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final UUID MERCHANT = UUID.fromString("7e726c28-4a72-4fea-9ec1-c56b08262ddc");
    private static final Instant CHANGED_AT = Instant.parse("2026-11-20T09:30:00Z");

    private final LifecycleEventParser parser = new LifecycleEventParser(JSON);

    /** Exactly what merchant-service's OutboxWriter stores and its relay publishes. */
    private static String published(MerchantLifecycleChanged.MerchantStatus status, String email) {
        MerchantLifecycleChanged payload = new MerchantLifecycleChanged(MERCHANT, "Lanka Books", "user-1", email,
                MerchantLifecycleChanged.MerchantStatus.PENDING, status, null, new Actor("admin-1", ActorRole.ADMIN),
                CHANGED_AT);
        return JSON.writeValueAsString(EventEnvelope.wrap(payload, "merchant-service", "corr-1"));
    }

    @Test
    void theContractsExampleEventIsRead() throws IOException {
        String example;
        try (InputStream in = getClass().getResourceAsStream(
                "/contracts/events/examples/merchant-lifecycle.v1.example.json")) {
            assertThat(in).as("contract example on the test classpath").isNotNull();
            example = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }

        LifecycleEvent event = parser.parse(example).orElseThrow();

        assertThat(event.eventId()).isEqualTo(UUID.fromString("3e4f5a6b-7c8d-4e9f-a0b1-c2d3e4f5a6b7"));
        assertThat(event.standing()).isEqualTo(new MerchantStanding("00000000-0000-4000-8000-000000000001",
                MerchantStatus.APPROVED, "Lanka Books (Pvt) Ltd", "orders@lankabooks.lk",
                Instant.parse("2026-10-19T11:29:59Z")));
    }

    @ParameterizedTest
    @EnumSource(MerchantLifecycleChanged.MerchantStatus.class)
    void everyStatusMerchantServicePublishesIsRead(MerchantLifecycleChanged.MerchantStatus status) {
        MerchantStanding standing = parser.parse(published(status, "lankabooks@example.com")).orElseThrow().standing();

        assertThat(standing.merchantId()).isEqualTo(MERCHANT.toString());
        assertThat(standing.status().name()).isEqualTo(status.name());
        assertThat(standing.businessName()).isEqualTo("Lanka Books");
        assertThat(standing.contactEmail()).isEqualTo("lankabooks@example.com");
        assertThat(standing.changedAt()).as("the time of the change, not of publishing").isEqualTo(CHANGED_AT);
    }

    @Test
    void theEnvelopesEventIdIsUsedForDeduplication() {
        String message = published(MerchantLifecycleChanged.MerchantStatus.APPROVED, null);
        UUID published = UUID.fromString(JSON.readTree(message).get("eventId").asString());

        assertThat(parser.parse(message).orElseThrow().eventId()).isEqualTo(published);
    }

    @Test
    void aMissingOrBlankContactEmailIsStoredAsNone() {
        assertThat(parser.parse(published(MerchantLifecycleChanged.MerchantStatus.APPROVED, null))
                .orElseThrow().standing().contactEmail()).isNull();
        assertThat(parser.parse(published(MerchantLifecycleChanged.MerchantStatus.APPROVED, " "))
                .orElseThrow().standing().contactEmail()).isNull();
    }

    @Test
    void unknownFieldsAreIgnoredSoTheProducerCanAddSome() {
        String message = published(MerchantLifecycleChanged.MerchantStatus.APPROVED, null)
                .replaceFirst("\\{", "{\"schemaHint\":\"x\",").replace("\"businessName\"", "\"tier\":\"gold\",\"businessName\"");

        assertThat(parser.parse(message)).isPresent();
    }

    @Test
    void anotherEventTypeOrVersionIsSkipped() {
        String message = published(MerchantLifecycleChanged.MerchantStatus.APPROVED, null);

        assertThat(parser.parse(message.replace("\"MerchantLifecycleChanged\"", "\"MerchantRenamed\"")))
                .isEqualTo(Optional.empty());
        assertThat(parser.parse(message.replace("\"eventVersion\":1", "\"eventVersion\":2")))
                .isEqualTo(Optional.empty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "not json", "[1,2]", "\"text\"", "{\"eventType\":\"MerchantLifecycleChanged\""})
    void somethingThatIsNotAnEnvelopeIsMalformed(String message) {
        assertThatThrownBy(() -> parser.parse(message)).isInstanceOf(MalformedEventException.class);
    }

    @Test
    void aNullMessageIsMalformed() {
        assertThatThrownBy(() -> parser.parse(null)).isInstanceOf(MalformedEventException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"merchantId", "status", "businessName", "changedAt"})
    void aLifecycleEventWithoutAFieldTheCacheNeedsIsMalformed(String field) {
        String message = published(MerchantLifecycleChanged.MerchantStatus.APPROVED, null)
                .replaceFirst("\"" + field + "\":(\"[^\"]*\"|null)", "\"" + field + "\":null");

        assertThatThrownBy(() -> parser.parse(message)).isInstanceOf(MalformedEventException.class);
    }

    @Test
    void anEnvelopeWithoutAnEventIdOrDataIsMalformed() {
        String message = published(MerchantLifecycleChanged.MerchantStatus.APPROVED, null);

        assertThatThrownBy(() -> parser.parse(message.replaceFirst("\"eventId\":\"[^\"]+\"", "\"eventId\":\"nope\"")))
                .isInstanceOf(MalformedEventException.class);
        assertThatThrownBy(() -> parser.parse(message.replaceFirst("\"data\":\\{.*}}$", "\"data\":null}")))
                .isInstanceOf(MalformedEventException.class);
    }

    @Test
    void aStatusOutsideTheContractIsMalformed() {
        String message = published(MerchantLifecycleChanged.MerchantStatus.APPROVED, null)
                .replace("\"status\":\"APPROVED\"", "\"status\":\"ARCHIVED\"");

        assertThatThrownBy(() -> parser.parse(message)).isInstanceOf(MalformedEventException.class);
    }
}
