package lk.swiftdrop.parcel.application.command;

import lk.swiftdrop.common.security.AuthenticatedUser;
import lk.swiftdrop.events.common.Types.Location;
import lk.swiftdrop.events.common.Types.ParcelSource;
import lk.swiftdrop.events.common.Types.ParcelStatus;
import lk.swiftdrop.events.parcel.ParcelCreated;
import lk.swiftdrop.outbox.OutboxWriter;
import lk.swiftdrop.parcel.ParcelFixtures;
import lk.swiftdrop.parcel.application.exception.PricingUnavailableException;
import lk.swiftdrop.parcel.application.port.out.ParcelRepository;
import lk.swiftdrop.parcel.application.port.out.PricingPort;
import lk.swiftdrop.parcel.domain.exception.QuoteExpiredException;
import lk.swiftdrop.parcel.domain.exception.QuoteMismatchException;
import lk.swiftdrop.parcel.domain.model.Address;
import lk.swiftdrop.parcel.domain.model.Parcel;
import lk.swiftdrop.parcel.domain.model.TrackingNumber;
import lk.swiftdrop.parcel.domain.model.TrackingNumberFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionOperations;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreateParcelUseCaseTest {

    private static final Clock CLOCK = Clock.fixed(ParcelFixtures.NOW, ZoneOffset.UTC);
    private static final UUID QUOTE_ID = UUID.randomUUID();
    private static final AuthenticatedUser CUSTOMER = ParcelFixtures.customer("cust-1");
    private static final CreateParcelCommand COMMAND = new CreateParcelCommand(QUOTE_ID, ParcelFixtures.PICKUP,
            ParcelFixtures.DROPOFF, ParcelFixtures.RECIPIENT, "Leave at reception");

    @Mock
    private PricingPort pricing;
    @Mock
    private ParcelRepository parcels;
    @Mock
    private OutboxWriter outbox;

    private CreateParcelUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new CreateParcelUseCase(pricing, parcels, new TrackingNumberFactory(CLOCK, new Random(7)), outbox,
                TransactionOperations.withoutTransaction(), CLOCK);
        lenient().when(parcels.save(any())).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    void booksAParcelFromAValidQuote() {
        when(pricing.findQuote(QUOTE_ID))
                .thenReturn(Optional.of(ParcelFixtures.quote(QUOTE_ID, ParcelFixtures.NOW.plusSeconds(600))));

        Parcel parcel = useCase.create(CUSTOMER, COMMAND);

        assertThat(parcel.status()).isEqualTo(ParcelStatus.CREATED);
        assertThat(parcel.source()).isEqualTo(ParcelSource.CUSTOMER);
        assertThat(parcel.customerId()).isEqualTo("cust-1");
        assertThat(TrackingNumber.isValid(parcel.trackingNumber().value())).isTrue();
        assertThat(parcel.createdAt()).isEqualTo(ParcelFixtures.NOW);
        assertThat(parcel.history()).hasSize(1);
        verify(parcels).save(parcel);
    }

    @Test
    void copiesTheFeeWeightAndSizeFromTheQuoteNotFromTheCaller() {
        when(pricing.findQuote(QUOTE_ID))
                .thenReturn(Optional.of(ParcelFixtures.quote(QUOTE_ID, ParcelFixtures.NOW.plusSeconds(600))));

        Parcel parcel = useCase.create(CUSTOMER, COMMAND);

        assertThat(parcel.details().fee().amount()).isEqualByComparingTo("650.00");
        assertThat(parcel.details().fee().currency()).isEqualTo("LKR");
        assertThat(parcel.details().weightKg()).isEqualByComparingTo(new BigDecimal("2.00"));
        assertThat(parcel.details().quoteId()).isEqualTo(QUOTE_ID);
        assertThat(parcel.details().pickupZone()).isEqualTo("WP-COL");
        assertThat(parcel.details().dropoffZone()).isEqualTo("CP-KDY");
    }

    @Test
    void publishesParcelCreatedWithCoarseLocationsOnly() {
        when(pricing.findQuote(QUOTE_ID))
                .thenReturn(Optional.of(ParcelFixtures.quote(QUOTE_ID, ParcelFixtures.NOW.plusSeconds(600))));

        Parcel parcel = useCase.create(CUSTOMER, COMMAND);

        ArgumentCaptor<ParcelCreated> published = ArgumentCaptor.forClass(ParcelCreated.class);
        verify(outbox).publish(published.capture());
        ParcelCreated event = published.getValue();
        assertThat(event.parcelId()).isEqualTo(parcel.id());
        assertThat(event.trackingNumber()).isEqualTo(parcel.trackingNumber().value());
        assertThat(event.pickup()).isEqualTo(new Location("Colombo 03", "Colombo", "WP-COL"));
        assertThat(event.dropoff()).isEqualTo(new Location("Kandy", "Kandy", "CP-KDY"));
        assertThat(event.contacts().recipientPhone()).isEqualTo("0712345678");
        assertThat(event.contacts().senderEmail()).isEqualTo("cust-1@example.com");
        assertThat(event.toString()).doesNotContain("Galle Road").doesNotContain("Peradeniya Road");
    }

    @Test
    void anUnknownQuoteIsRejectedAndNothingIsStored() {
        when(pricing.findQuote(QUOTE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.create(CUSTOMER, COMMAND)).isInstanceOf(QuoteExpiredException.class);

        verifyNoInteractions(parcels, outbox);
    }

    @Test
    void aQuotePastItsExpiryIsRejectedEvenIfPricingStillReturnsIt() {
        when(pricing.findQuote(QUOTE_ID))
                .thenReturn(Optional.of(ParcelFixtures.quote(QUOTE_ID, ParcelFixtures.NOW)));

        assertThatThrownBy(() -> useCase.create(CUSTOMER, COMMAND)).isInstanceOf(QuoteExpiredException.class);

        verifyNoInteractions(parcels, outbox);
    }

    @Test
    void aQuotePricedForAnotherRouteIsRejected() {
        when(pricing.findQuote(QUOTE_ID))
                .thenReturn(Optional.of(ParcelFixtures.quote(QUOTE_ID, ParcelFixtures.NOW.plusSeconds(600))));
        Address galle = new Address("1 Lighthouse Street", null, "Galle", "Galle", null, null, null);
        CreateParcelCommand elsewhere =
                new CreateParcelCommand(QUOTE_ID, ParcelFixtures.PICKUP, galle, ParcelFixtures.RECIPIENT, null);

        assertThatThrownBy(() -> useCase.create(CUSTOMER, elsewhere)).isInstanceOf(QuoteMismatchException.class);

        verifyNoInteractions(parcels, outbox);
    }

    @Test
    void districtsAreComparedWithoutRegardToCaseOrSpacing() {
        when(pricing.findQuote(QUOTE_ID))
                .thenReturn(Optional.of(ParcelFixtures.quote(QUOTE_ID, ParcelFixtures.NOW.plusSeconds(600))));
        Address kandy = new Address("45 Peradeniya Road", null, "Kandy", " kandy ", null, null, null);
        CreateParcelCommand command =
                new CreateParcelCommand(QUOTE_ID, ParcelFixtures.PICKUP, kandy, ParcelFixtures.RECIPIENT, null);

        assertThat(useCase.create(CUSTOMER, command).status()).isEqualTo(ParcelStatus.CREATED);
    }

    @Test
    void whenPricingIsUnavailableNothingIsStored() {
        when(pricing.findQuote(QUOTE_ID)).thenThrow(new PricingUnavailableException(new RuntimeException("timeout")));

        assertThatThrownBy(() -> useCase.create(CUSTOMER, COMMAND)).isInstanceOf(PricingUnavailableException.class);

        verifyNoInteractions(parcels, outbox);
    }
}
