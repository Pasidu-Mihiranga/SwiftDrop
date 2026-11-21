package lk.swiftdrop.merchant;

import com.networknt.schema.InputFormat;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaValidatorsConfig;
import com.networknt.schema.SpecVersion;
import lk.swiftdrop.common.security.AuthenticatedUser;
import lk.swiftdrop.merchant.application.command.MerchantOnboardingService;
import lk.swiftdrop.merchant.application.model.PagedResult;
import lk.swiftdrop.merchant.application.port.out.MerchantRepository;
import lk.swiftdrop.merchant.application.query.MerchantQueryService;
import lk.swiftdrop.merchant.domain.exception.BrNumberAlreadyRegisteredException;
import lk.swiftdrop.merchant.domain.exception.InvalidMerchantTransitionException;
import lk.swiftdrop.merchant.domain.exception.MerchantApplicationExistsException;
import lk.swiftdrop.merchant.domain.model.BusinessDetails;
import lk.swiftdrop.merchant.domain.model.Merchant;
import lk.swiftdrop.merchant.domain.model.MerchantStatus;
import lk.swiftdrop.merchant.domain.model.PickupAddress;
import lk.swiftdrop.common.web.error.ConflictException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import lk.swiftdrop.merchant.application.exception.IdentityProviderUnavailableException;
import lk.swiftdrop.merchant.application.port.out.IdentityProvisioner;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionOperations;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Onboarding against real PostgreSQL: the Flyway migrations and their constraints, encryption at
 * rest, duplicate handling, the lifecycle and the outbox rows it writes.
 */
@Testcontainers
@SpringBootTest(properties = {
        // test-only key (32 bytes, base64); real deployments read PII_ENCRYPTION_KEY
        "swiftdrop.pii.encryption-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        // the outbox rows are inspected directly, so the relay must not publish and mark them
        "swiftdrop.outbox.relay-enabled=false"})
class MerchantOnboardingIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16.15-alpine");
    @Container
    @ServiceConnection
    static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:4.1.2");

    private static final String SCHEMA_BASE = "https://swiftdrop.lk/contracts/events/";
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final AuthenticatedUser ADMIN = MerchantFixtures.admin("admin-1");

    @Autowired
    private MerchantOnboardingService onboarding;
    @Autowired
    private MerchantQueryService queries;
    @Autowired
    private MerchantRepository repository;
    @Autowired
    private TransactionOperations transaction;
    @Autowired
    private JdbcTemplate jdbc;
    /** Keycloak itself is exercised by KeycloakAdminAdapterIT; here it only has to succeed. */
    @MockitoBean
    private IdentityProvisioner identity;

    @BeforeEach
    void cleanUp() {
        jdbc.update("DELETE FROM outbox_event");
        jdbc.update("DELETE FROM api_credential");
        jdbc.update("DELETE FROM merchant");
        when(identity.provisionClient(any())).thenAnswer(call -> "merchant-" + call.getArgument(0));
    }

    @Test
    void approvalRecordsTheKeycloakClientOnceWithoutAnySecret() {
        Merchant merchant = onboarding.register(MerchantFixtures.user("user-1"), business("PV12345"));

        onboarding.approve(ADMIN, merchant.id());

        Map<String, Object> credential = jdbc.queryForMap("SELECT * FROM api_credential WHERE merchant_id = ?", merchant.id());
        assertThat(credential.get("keycloak_client_id")).isEqualTo("merchant-" + merchant.id());
        assertThat(credential.get("revoked_at")).isNull();
        assertThat(credential.keySet()).noneMatch(column -> column.contains("secret"));
        verify(identity).grantMerchantRole("user-1", merchant.id());
    }

    @Test
    void whenKeycloakIsDownTheMerchantStaysPendingWithNoCredentialOrEvent() {
        Merchant merchant = onboarding.register(MerchantFixtures.user("user-1"), business("PV12345"));
        when(identity.provisionClient(merchant.id()))
                .thenThrow(new IdentityProviderUnavailableException("The identity provider is not reachable right now"));

        assertThatThrownBy(() -> onboarding.approve(ADMIN, merchant.id()))
                .isInstanceOf(IdentityProviderUnavailableException.class);

        assertThat(jdbc.queryForObject("SELECT status FROM merchant WHERE id = ?", String.class, merchant.id()))
                .isEqualTo("PENDING");
        assertThat(count("SELECT count(*) FROM api_credential")).isZero();
        assertThat(count("SELECT count(*) FROM outbox_event")).isEqualTo(1);
    }

    private static BusinessDetails business(String brNumber) {
        return new BusinessDetails("Lanka Books", brNumber, "lankabooks@example.com", "+94771234567",
                "https://lankabooks.example.com", new PickupAddress("12 Temple Road", "Level 2", "Kandy", "Kandy", "20000"));
    }

    private int count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    @Test
    void anApplicationIsStoredPendingWithItsEventInTheSameCommit() {
        Merchant merchant = onboarding.register(MerchantFixtures.user("user-1"), business("PV 12345"));

        assertThat(merchant.version()).isZero();
        Map<String, Object> row = jdbc.queryForMap("SELECT status, br_number FROM merchant WHERE id = ?", merchant.id());
        assertThat(row.get("status")).isEqualTo("PENDING");
        assertThat(row.get("br_number")).isEqualTo("PV12345");

        Map<String, Object> outbox = jdbc.queryForMap(
                "SELECT event_type, topic, message_key, payload::text AS payload FROM outbox_event");
        assertThat(outbox.get("event_type")).isEqualTo("MerchantLifecycleChanged");
        assertThat(outbox.get("topic")).isEqualTo("swiftdrop.merchant.lifecycle.v1");
        assertThat(outbox.get("message_key")).isEqualTo(merchant.id().toString());
        String payload = (String) outbox.get("payload");
        assertThat(validate(payload)).isEmpty();
        assertThat(payload).doesNotContain("+94771234567").doesNotContain("Temple Road");
    }

    @Test
    void thePhoneAndPickupAddressAreEncryptedAtRestAndReadBackInClear() {
        Merchant merchant = onboarding.register(MerchantFixtures.user("user-1"), business("PV12345"));

        Map<String, Object> row = jdbc.queryForMap(
                "SELECT phone_enc, pickup_address_enc FROM merchant WHERE id = ?", merchant.id());
        assertThat(row.values()).allSatisfy(value -> assertThat((String) value).startsWith("v1:")
                .doesNotContain("94771234567").doesNotContain("Temple").doesNotContain("Kandy"));

        Merchant reloaded = queries.mine("user-1");
        assertThat(reloaded.business().contactPhone()).isEqualTo("+94771234567");
        assertThat(reloaded.business().pickupAddress().line1()).isEqualTo("12 Temple Road");
        assertThat(reloaded.business().pickupAddress().line2()).isEqualTo("Level 2");
    }

    @Test
    void aRegistrationNumberInUseIsRefusedEvenWhenWrittenDifferently() {
        onboarding.register(MerchantFixtures.user("user-1"), business("PV12345"));

        assertThatThrownBy(() -> onboarding.register(MerchantFixtures.user("user-2"), business("pv 12345")))
                .isInstanceOfSatisfying(BrNumberAlreadyRegisteredException.class,
                        e -> assertThat(e.status().value()).isEqualTo(409));
        assertThat(count("SELECT count(*) FROM merchant")).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM outbox_event")).isEqualTo(1);
    }

    @Test
    void aUserCannotHoldTwoApplicationsAtOnce() {
        onboarding.register(MerchantFixtures.user("user-1"), business("PV111"));

        assertThatThrownBy(() -> onboarding.register(MerchantFixtures.user("user-1"), business("PV222")))
                .isInstanceOf(MerchantApplicationExistsException.class);
    }

    @Test
    void aRejectedApplicantCanCorrectTheirDetailsAndApplyAgain() {
        Merchant first = onboarding.register(MerchantFixtures.user("user-1"), business("PV12345"));
        onboarding.reject(ADMIN, first.id(), "BR certificate is unreadable");

        Merchant second = onboarding.register(MerchantFixtures.user("user-1"), business("PV12345"));

        assertThat(second.id()).isNotEqualTo(first.id());
        assertThat(second.status()).isEqualTo(MerchantStatus.PENDING);
        assertThat(queries.mine("user-1").id()).isEqualTo(second.id());
        assertThat(count("SELECT count(*) FROM merchant WHERE owner_user_id = 'user-1'")).isEqualTo(2);
    }

    @Test
    void theUniqueIndexSettlesARaceThePreCheckMissed() {
        onboarding.register(MerchantFixtures.user("user-1"), business("PV12345"));

        // Straight to the repository, as a second request would be after both passed the check.
        Merchant rival = Merchant.register("user-2", business("PV12345"), MerchantFixtures.NOW);
        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> repository.save(rival)))
                .isInstanceOf(BrNumberAlreadyRegisteredException.class);
    }

    @Test
    void theFullLifecycleWritesOneEventPerChange() {
        Merchant merchant = onboarding.register(MerchantFixtures.user("user-1"), business("PV12345"));

        Merchant approved = onboarding.approve(ADMIN, merchant.id());
        Merchant suspended = onboarding.suspend(ADMIN, merchant.id());

        assertThat(approved.status()).isEqualTo(MerchantStatus.APPROVED);
        assertThat(suspended.status()).isEqualTo(MerchantStatus.SUSPENDED);
        assertThat(suspended.version()).isEqualTo(2);
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT status, reviewed_by, rejection_reason FROM merchant WHERE id = ?", merchant.id());
        assertThat(row.get("status")).isEqualTo("SUSPENDED");
        assertThat(row.get("reviewed_by")).isEqualTo("admin-1");
        assertThat(row.get("rejection_reason")).isNull();

        List<String> payloads = jdbc.queryForList(
                "SELECT payload::text FROM outbox_event ORDER BY created_at", String.class);
        assertThat(payloads).hasSize(3).allSatisfy(payload -> assertThat(validate(payload)).isEmpty());
        assertThat(payloads).extracting(payload -> JSON.readTree(payload).get("data").get("status").asString())
                .containsExactly("PENDING", "APPROVED", "SUSPENDED");
    }

    @Test
    void aRefusedTransitionChangesNothing() {
        Merchant merchant = onboarding.register(MerchantFixtures.user("user-1"), business("PV12345"));

        assertThatThrownBy(() -> onboarding.suspend(ADMIN, merchant.id()))
                .isInstanceOf(InvalidMerchantTransitionException.class);
        assertThat(jdbc.queryForObject("SELECT status FROM merchant WHERE id = ?", String.class, merchant.id()))
                .isEqualTo("PENDING");
        assertThat(count("SELECT count(*) FROM outbox_event")).isEqualTo(1);
    }

    @Test
    void aDecisionMadeOnAStaleCopyIsAConflict() {
        Merchant stale = onboarding.register(MerchantFixtures.user("user-1"), business("PV12345"));
        onboarding.approve(ADMIN, stale.id());

        // A second admin still holds the version from before the approval.
        Merchant rejectedFromStaleCopy = stale.reject("admin-2", "Too slow", MerchantFixtures.NOW);
        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> repository.save(rejectedFromStaleCopy)))
                .isInstanceOfSatisfying(ConflictException.class,
                        e -> assertThat(e.code()).isEqualTo("MERCHANT_MODIFIED"));
    }

    @Test
    void theAdminQueueListsOneStatusOldestFirstOnePageAtATime() {
        Merchant first = onboarding.register(MerchantFixtures.user("user-1"), business("PV1"));
        Merchant second = onboarding.register(MerchantFixtures.user("user-2"), business("PV2"));
        Merchant third = onboarding.register(MerchantFixtures.user("user-3"), business("PV3"));
        onboarding.approve(ADMIN, onboarding.register(MerchantFixtures.user("user-4"), business("PV4")).id());

        PagedResult<Merchant> firstPage = queries.byStatus(MerchantStatus.PENDING, 0, 2);
        PagedResult<Merchant> secondPage = queries.byStatus(MerchantStatus.PENDING, 1, 2);

        assertThat(firstPage.content()).extracting(Merchant::id).containsExactly(first.id(), second.id());
        assertThat(firstPage.totalElements()).isEqualTo(3);
        assertThat(firstPage.totalPages()).isEqualTo(2);
        assertThat(secondPage.content()).extracting(Merchant::id).containsExactly(third.id());
        assertThat(queries.byStatus(MerchantStatus.APPROVED, 0, 20).content()).hasSize(1);
    }

    @Test
    void theDatabaseRefusesRowsThatBreakTheLifecycleRules() {
        String insert = """
                INSERT INTO merchant (id, owner_user_id, business_name, br_number, contact_email, phone_enc,
                    pickup_address_enc, status, reviewed_by, reviewed_at, rejection_reason, created_at, updated_at)
                VALUES (?, 'user-x', 'Shop', ?, 'a@b.example', 'v1:x', 'v1:x', ?, ?, ?, ?, now(), now())
                """;
        java.sql.Timestamp now = new java.sql.Timestamp(System.currentTimeMillis());

        // a decision without the admin who made it
        assertThatThrownBy(() -> jdbc.update(insert, UUID.randomUUID(), "BR1", "APPROVED", null, null, null))
                .isInstanceOf(DataIntegrityViolationException.class);
        // a rejection without a reason
        assertThatThrownBy(() -> jdbc.update(insert, UUID.randomUUID(), "BR2", "REJECTED", "admin", now, null))
                .isInstanceOf(DataIntegrityViolationException.class);
        // a reason on something that was not rejected
        assertThatThrownBy(() -> jdbc.update(insert, UUID.randomUUID(), "BR3", "APPROVED", "admin", now, "why"))
                .isInstanceOf(DataIntegrityViolationException.class);
        // a status outside the lifecycle
        assertThatThrownBy(() -> jdbc.update(insert, UUID.randomUUID(), "BR4", "DELETED", "admin", now, null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private static Set<?> validate(String json) {
        JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012,
                builder -> builder.schemaMappers(m -> m.mapPrefix(SCHEMA_BASE, "classpath:contracts/events/")));
        JsonSchema schema = factory.getSchema(SchemaLocation.of(SCHEMA_BASE + "merchant-lifecycle.v1.schema.json"),
                SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build());
        return schema.validate(json, InputFormat.JSON);
    }
}
