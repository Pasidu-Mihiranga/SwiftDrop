package lk.swiftdrop.pricing.application.query;

import lk.swiftdrop.common.web.error.ResourceNotFoundException;
import lk.swiftdrop.pricing.application.model.QuoteSnapshot;
import lk.swiftdrop.pricing.application.port.out.QuoteStore;
import lk.swiftdrop.pricing.domain.model.FeeBreakdown;
import lk.swiftdrop.pricing.domain.model.PricingRule;
import lk.swiftdrop.pricing.domain.model.ServiceLevel;
import lk.swiftdrop.pricing.domain.model.SizeClassCode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class QuoteLookupServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-25T00:10:00Z");

    private final QuoteStore store = mock(QuoteStore.class);
    private final QuoteLookupService lookup = new QuoteLookupService(store, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void returnsStoredSnapshotWithoutRecalculatingOrReplacingItsFields() {
        UUID id = UUID.randomUUID();
        QuoteSnapshot snapshot = snapshot(id, NOW.plusSeconds(300));
        when(store.findById(id)).thenReturn(Optional.of(snapshot));

        assertThat(lookup.get(id)).isSameAs(snapshot);
    }

    @Test
    void unknownAndExpiredQuotesHaveTheSameNotFoundCode() {
        UUID id = UUID.randomUUID();
        when(store.findById(id)).thenReturn(Optional.empty());
        assertNotFound(id);

        when(store.findById(id)).thenReturn(Optional.of(snapshot(id, NOW)));
        assertNotFound(id);
    }

    @Test
    void rejectsAStoredSnapshotWhoseIdentityDoesNotMatchTheRequestedKey() {
        UUID requested = UUID.randomUUID();
        when(store.findById(requested)).thenReturn(Optional.of(snapshot(UUID.randomUUID(), NOW.plusSeconds(300))));

        assertNotFound(requested);
    }

    private void assertNotFound(UUID id) {
        assertThatThrownBy(() -> lookup.get(id))
                .isInstanceOfSatisfying(ResourceNotFoundException.class,
                        error -> assertThat(error.code()).isEqualTo("QUOTE_NOT_FOUND"));
    }

    private static QuoteSnapshot snapshot(UUID id, Instant expiresAt) {
        return new QuoteSnapshot(id, new QuoteSnapshot.Money(new BigDecimal("500.00"), "LKR"),
                List.of(new FeeBreakdown.Line(PricingRule.BASE, new BigDecimal("500.00"))),
                "WP", "CP", "Colombo", "Kandy", new BigDecimal("2"), SizeClassCode.M,
                ServiceLevel.STANDARD, BigDecimal.ZERO, NOW.minusSeconds(600), expiresAt);
    }
}
