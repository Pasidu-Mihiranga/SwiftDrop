package lk.swiftdrop.events;

import com.networknt.schema.InputFormat;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaValidatorsConfig;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import lk.swiftdrop.events.common.Types.Actor;
import lk.swiftdrop.events.common.Types.ActorRole;
import lk.swiftdrop.events.common.Types.FailureReason;
import lk.swiftdrop.events.common.Types.Location;
import lk.swiftdrop.events.common.Types.Money;
import lk.swiftdrop.events.common.Types.ParcelSource;
import lk.swiftdrop.events.common.Types.ParcelStatus;
import lk.swiftdrop.events.common.Types.ServiceLevel;
import lk.swiftdrop.events.common.Types.SizeClass;
import lk.swiftdrop.events.dispatch.ParcelAssigned;
import lk.swiftdrop.events.merchant.MerchantLifecycleChanged;
import lk.swiftdrop.events.merchant.MerchantLifecycleChanged.MerchantStatus;
import lk.swiftdrop.events.parcel.ParcelCreated;
import lk.swiftdrop.events.parcel.ParcelStatusChanged;
import lk.swiftdrop.events.parcel.ParcelStatusChanged.Proof;
import lk.swiftdrop.events.parcel.ParcelStatusChanged.ProofType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Keeps the Java records and the JSON Schemas in /contracts/events in sync:
 * every example validates, every record serialises to a valid event, and examples deserialise into records.
 */
class EventContractTest {

    private static final String BASE = "https://swiftdrop.lk/contracts/events/";
    private static final JsonSchemaFactory FACTORY = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012,
            builder -> builder.schemaMappers(m -> m.mapPrefix(BASE, "classpath:contracts/events/")));
    private static final SchemaValidatorsConfig CONFIG = SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build();

    private final JsonMapper mapper = JsonMapper.builder().build();

    private static Set<ValidationMessage> validate(String schemaFile, String json) {
        JsonSchema schema = FACTORY.getSchema(SchemaLocation.of(BASE + schemaFile), CONFIG);
        return schema.validate(json, InputFormat.JSON);
    }

    private static String example(String name) throws IOException {
        try (InputStream in = EventContractTest.class.getResourceAsStream("/contracts/events/examples/" + name)) {
            assertThat(in).as("example %s on classpath", name).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"parcel-created.v1", "parcel-status-changed.v1", "parcel-assigned.v1",
            "merchant-lifecycle.v1", "webhook-dead-letter.v1"})
    void examplesMatchTheirSchemas(String contract) throws IOException {
        assertThat(validate(contract + ".schema.json", example(contract + ".example.json"))).isEmpty();
    }

    @Test
    void parcelCreatedRecordProducesValidEvent() {
        ParcelCreated payload = new ParcelCreated(UUID.randomUUID(), "SD261020ABCDEFG", ParcelSource.CUSTOMER,
                "cust-1", null, null,
                new Location("Colombo 07", "Colombo", "WP-COL"), new Location("Galle", "Galle", null),
                SizeClass.M, new BigDecimal("3.25"), ServiceLevel.EXPRESS, Money.lkr(new BigDecimal("1200.00")),
                new ParcelCreated.Contacts("Nimali", "nimali@example.com", "0771234567", "Saman", "0712345678"),
                Instant.now());

        String json = mapper.writeValueAsString(EventEnvelope.wrap(payload, "parcel-service", "corr-1"));

        assertThat(validate("parcel-created.v1.schema.json", json)).isEmpty();
        assertThat(payload.topic()).isEqualTo(Topics.PARCEL_CREATED);
        assertThat(payload.partitionKey()).isEqualTo(payload.parcelId().toString());
        assertThat(json).doesNotContain("partitionKey").doesNotContain("\"topic\"");
    }

    @Test
    void statusChangedRecordProducesValidEvent() {
        ParcelStatusChanged delivered = new ParcelStatusChanged(UUID.randomUUID(), "SD261020ABCDEFG", "cust-1", null,
                "courier-1", ParcelStatus.OUT_FOR_DELIVERY, ParcelStatus.DELIVERED, null, null,
                new Proof(ProofType.NAME, "Saman", null), null,
                new Actor("courier-1", ActorRole.COURIER), Instant.now());

        String json = mapper.writeValueAsString(EventEnvelope.wrap(delivered, "parcel-service", null));

        assertThat(validate("parcel-status-changed.v1.schema.json", json)).isEmpty();
        assertThat(delivered.toStatus().isTerminal()).isTrue();
    }

    @Test
    void schemaRejectsFailedStatusWithoutReason() {
        ParcelStatusChanged failedWithoutReason = new ParcelStatusChanged(UUID.randomUUID(), "SD261020ABCDEFG", null, null,
                "courier-1", ParcelStatus.OUT_FOR_DELIVERY, ParcelStatus.FAILED, "nobody home", null, null, null,
                new Actor("courier-1", ActorRole.COURIER), Instant.now());

        String json = mapper.writeValueAsString(EventEnvelope.wrap(failedWithoutReason, "parcel-service", null));

        assertThat(validate("parcel-status-changed.v1.schema.json", json)).isNotEmpty();
    }

    @Test
    void assignedAndMerchantRecordsProduceValidEvents() {
        ParcelAssigned assigned = new ParcelAssigned(UUID.randomUUID(), UUID.randomUUID(), "SD261020ABCDEFG", "courier-1",
                null, LocalDate.now(), new Actor("dispatcher-1", ActorRole.DISPATCHER), Instant.now());
        MerchantLifecycleChanged approved = new MerchantLifecycleChanged(UUID.randomUUID(), "Lanka Books", "owner-1",
                "orders@lankabooks.lk", MerchantStatus.PENDING, MerchantStatus.APPROVED, null, Actor.SYSTEM, Instant.now());

        assertThat(validate("parcel-assigned.v1.schema.json",
                mapper.writeValueAsString(EventEnvelope.wrap(assigned, "dispatch-service", "c")))).isEmpty();
        assertThat(validate("merchant-lifecycle.v1.schema.json",
                mapper.writeValueAsString(EventEnvelope.wrap(approved, "merchant-service", "c")))).isEmpty();
        assertThat(assigned.topic()).isEqualTo(Topics.PARCEL_ASSIGNED);
        assertThat(approved.partitionKey()).isEqualTo(approved.merchantId().toString());
    }

    @Test
    void examplesDeserialiseIntoRecords() throws IOException {
        EventEnvelope<ParcelStatusChanged> envelope = mapper.readValue(example("parcel-status-changed.v1.example.json"),
                new TypeReference<>() { });

        assertThat(envelope.eventType()).isEqualTo(ParcelStatusChanged.TYPE);
        assertThat(envelope.data().toStatus()).isEqualTo(ParcelStatus.DELIVERED);
        assertThat(envelope.data().proof().type()).isEqualTo(ProofType.PHOTO);
        assertThat(envelope.data().changedBy().role()).isEqualTo(ActorRole.COURIER);

        EventEnvelope<ParcelCreated> created = mapper.readValue(example("parcel-created.v1.example.json"), new TypeReference<>() { });
        assertThat(created.data().fee().currency()).isEqualTo("LKR");
        assertThat(created.data().contacts().recipientName()).isEqualTo("Kamal Perera");

        EventEnvelope<ParcelAssigned> assigned = mapper.readValue(example("parcel-assigned.v1.example.json"), new TypeReference<>() { });
        assertThat(assigned.data().runDate()).isEqualTo(LocalDate.parse("2026-10-20"));

        EventEnvelope<MerchantLifecycleChanged> merchant = mapper.readValue(example("merchant-lifecycle.v1.example.json"), new TypeReference<>() { });
        assertThat(merchant.data().status()).isEqualTo(MerchantStatus.APPROVED);
    }

    @Test
    void failureReasonsMatchSchemaVocabulary() {
        assertThat(FailureReason.values()).extracting(Enum::name)
                .containsExactly("RECIPIENT_UNAVAILABLE", "WRONG_ADDRESS", "REFUSED", "DAMAGED", "OTHER");
    }
}
