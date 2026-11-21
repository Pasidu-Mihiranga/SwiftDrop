package lk.swiftdrop.pricing.application.command;

import lk.swiftdrop.common.web.error.ApiException;
import lk.swiftdrop.common.web.error.ResourceNotFoundException;
import lk.swiftdrop.pricing.application.command.PricingConfigurationAdminService.BandDraft;
import lk.swiftdrop.pricing.application.model.SameDayVehicleConfig;
import lk.swiftdrop.pricing.application.model.SpecialHandlingConfig;
import lk.swiftdrop.pricing.application.model.Versioned;
import lk.swiftdrop.pricing.application.port.out.DistanceBandAdminRepository;
import lk.swiftdrop.pricing.application.port.out.PricingSettingsAdminRepository;
import lk.swiftdrop.pricing.application.port.out.SameDayVehicleAdminRepository;
import lk.swiftdrop.pricing.application.port.out.SpecialHandlingAdminRepository;
import lk.swiftdrop.pricing.domain.model.DistanceBand;
import lk.swiftdrop.pricing.domain.model.PricingSettings;
import lk.swiftdrop.pricing.domain.model.SameDayVehicle;
import lk.swiftdrop.pricing.domain.model.VehicleType;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PricingConfigurationAdminServiceTest {

    // 2026-11-24 01:30 in Colombo: the UTC date is still the 23rd, so "today" must come from the zone.
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-23T20:00:00Z"), ZoneId.of("Asia/Colombo"));
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 24);

    private final PricingSettingsAdminRepository settings = mock(PricingSettingsAdminRepository.class);
    private final DistanceBandAdminRepository bands = mock(DistanceBandAdminRepository.class);
    private final SpecialHandlingAdminRepository handling = mock(SpecialHandlingAdminRepository.class);
    private final SameDayVehicleAdminRepository vehicles = mock(SameDayVehicleAdminRepository.class);
    private final PricingConfigurationAdminService service =
            new PricingConfigurationAdminService(settings, bands, handling, vehicles, CLOCK);

    private static void assertCode(Throwable error, String code) {
        assertThat(error).isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo(code));
    }

    // ---------------------------------------------------------------- fee settings

    @Test
    void publishesSettingsWithACutoffFromToday() {
        when(settings.publish(any(), eq(TODAY))).thenAnswer(call ->
                new Versioned<>(call.getArgument(0, PricingSettings.class), TODAY, null));

        Versioned<PricingSettings> published = service.publishSettings(new BigDecimal("0.06"),
                new BigDecimal("0.18"), new BigDecimal("400.00"), LocalTime.of(14, 0), TODAY);

        assertThat(published.value().sameDayCutoff()).isEqualTo(LocalTime.of(14, 0));
        assertThat(published.value().fuelSurchargePct()).isEqualByComparingTo("0.06");
    }

    @Test
    void refusesSettingsFromAPastDay() {
        assertThatThrownBy(() -> service.publishSettings(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null,
                TODAY.minusDays(1)))
                .satisfies(error -> assertCode(error, "EFFECTIVE_DATE_IN_PAST"));
        assertThatThrownBy(() -> service.publishSettings(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null,
                null))
                .satisfies(error -> assertCode(error, "EFFECTIVE_DATE_IN_PAST"));
        verify(settings, never()).publish(any(), any());
    }

    @Test
    void refusesSettingsThatDoNotFollowEveryPublishedVersion() {
        when(settings.hasVersionStartingOnOrAfter(TODAY)).thenReturn(true);

        assertThatThrownBy(() -> service.publishSettings(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null,
                TODAY))
                .satisfies(error -> assertCode(error, "PRICING_SETTINGS_PERIOD_CONFLICT"));
    }

    @Test
    void turnsAnImpossibleRateIntoA422() {
        assertThatThrownBy(() -> service.publishSettings(BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO, null,
                TODAY))
                .satisfies(error -> assertCode(error, "INVALID_PRICING_SETTINGS"));
    }

    @Test
    void listsTheSettingsHistory() {
        List<Versioned<PricingSettings>> history = List.of(new Versioned<>(
                new PricingSettings(null, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO), TODAY, null));
        when(settings.findAllVersions()).thenReturn(history);

        assertThat(service.settingsHistory()).isSameAs(history);
    }

    // ---------------------------------------------------------------- distance bands

    @Test
    @SuppressWarnings("unchecked")
    void publishesACompleteBandSetInDistanceOrder() {
        when(bands.publishBands(anyList(), eq(TODAY))).thenReturn(List.of());

        service.publishBands(List.of(
                new BandDraft(new BigDecimal("50"), null, new BigDecimal("300"), 3, 2),
                new BandDraft(BigDecimal.ZERO, new BigDecimal("50"), BigDecimal.ZERO, 1, 1)), TODAY);

        ArgumentCaptor<List<DistanceBand>> published = ArgumentCaptor.forClass(List.class);
        verify(bands).publishBands(published.capture(), eq(TODAY));
        assertThat(published.getValue()).extracting(DistanceBand::minKm)
                .containsExactly(BigDecimal.ZERO, new BigDecimal("50"));
    }

    @Test
    void refusesABandSetWithAGapOrAnInvalidBand() {
        assertThatThrownBy(() -> service.publishBands(List.of(
                new BandDraft(BigDecimal.ZERO, new BigDecimal("30"), BigDecimal.ZERO, 1, 1),
                new BandDraft(new BigDecimal("40"), null, BigDecimal.ONE, 2, 1)), TODAY))
                .satisfies(error -> assertCode(error, "INVALID_DISTANCE_BANDS"));
        // Express slower than standard is refused by the band itself.
        assertThatThrownBy(() -> service.publishBands(List.of(
                new BandDraft(BigDecimal.ZERO, null, BigDecimal.ZERO, 1, 2)), TODAY))
                .satisfies(error -> assertCode(error, "INVALID_DISTANCE_BANDS"));
        verify(bands, never()).publishBands(anyList(), any());
    }

    @Test
    void refusesBandsThatDoNotFollowEveryPublishedSet() {
        when(bands.hasBandVersionStartingOnOrAfter(TODAY.plusDays(3))).thenReturn(true);

        assertThatThrownBy(() -> service.publishBands(List.of(
                new BandDraft(BigDecimal.ZERO, null, BigDecimal.ZERO, 1, 1)), TODAY.plusDays(3)))
                .satisfies(error -> assertCode(error, "DISTANCE_BAND_PERIOD_CONFLICT"));
        assertThatThrownBy(() -> service.publishBands(List.of(
                new BandDraft(BigDecimal.ZERO, null, BigDecimal.ZERO, 1, 1)), TODAY.minusDays(1)))
                .satisfies(error -> assertCode(error, "EFFECTIVE_DATE_IN_PAST"));
    }

    @Test
    void listsTheBandHistory() {
        when(bands.findAllBandVersions()).thenReturn(List.of());

        assertThat(service.bandHistory()).isEmpty();
    }

    // ---------------------------------------------------------------- handling options

    @Test
    void addsAHandlingOptionUnderAnUpperCaseCode() {
        when(handling.findByCode("OVERSIZE")).thenReturn(Optional.empty());
        when(handling.save(any())).thenAnswer(call -> call.getArgument(0));

        SpecialHandlingConfig added = service.addHandling(" oversize ", " Oversize item ", new BigDecimal("500"), 50);

        assertThat(added).isEqualTo(new SpecialHandlingConfig("OVERSIZE", "Oversize item", new BigDecimal("500"),
                true, 50));
    }

    @Test
    void refusesADuplicateOrMalformedHandlingCode() {
        when(handling.findByCode("FRAGILE")).thenReturn(Optional.of(
                new SpecialHandlingConfig("FRAGILE", "Fragile", BigDecimal.TEN, true, 10)));

        assertThatThrownBy(() -> service.addHandling("fragile", "Fragile", BigDecimal.TEN, 10))
                .satisfies(error -> assertCode(error, "SPECIAL_HANDLING_EXISTS"));
        assertThatThrownBy(() -> service.addHandling("1BAD", "Bad", BigDecimal.TEN, 10))
                .satisfies(error -> assertCode(error, "INVALID_SPECIAL_HANDLING"));
        assertThatThrownBy(() -> service.addHandling(null, "Bad", BigDecimal.TEN, 10))
                .satisfies(error -> assertCode(error, "INVALID_SPECIAL_HANDLING"));
        verify(handling, never()).save(any());
    }

    @Test
    void retiresAHandlingOptionWithoutChangingItsCode() {
        when(handling.findByCode("LIQUID")).thenReturn(Optional.of(
                new SpecialHandlingConfig("LIQUID", "Contains liquid", new BigDecimal("200"), true, 30)));
        when(handling.save(any())).thenAnswer(call -> call.getArgument(0));

        SpecialHandlingConfig updated = service.updateHandling("LIQUID", "Liquids", new BigDecimal("250"), false, 35);

        assertThat(updated).isEqualTo(new SpecialHandlingConfig("LIQUID", "Liquids", new BigDecimal("250"), false,
                35));
        assertThat(service.handlingOptions()).isEmpty();
    }

    @Test
    void refusesToUpdateAnUnknownHandlingOptionOrGiveItANegativeFee() {
        when(handling.findByCode("NOPE")).thenReturn(Optional.empty());
        when(handling.findByCode("LIQUID")).thenReturn(Optional.of(
                new SpecialHandlingConfig("LIQUID", "Contains liquid", new BigDecimal("200"), true, 30)));

        assertThatThrownBy(() -> service.updateHandling("NOPE", "x", BigDecimal.ONE, true, 1))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.updateHandling("LIQUID", "x", new BigDecimal("-1"), true, 1))
                .satisfies(error -> assertCode(error, "INVALID_SPECIAL_HANDLING"));
    }

    // ---------------------------------------------------------------- same-day vehicles

    private static SameDayVehicleConfig van() {
        return new SameDayVehicleConfig(new SameDayVehicle(VehicleType.VAN, new BigDecimal("100"),
                new BigDecimal("120"), new BigDecimal("1.8")), "Van", true);
    }

    @Test
    void updatesAVehiclesLimits() {
        when(vehicles.findByType(VehicleType.VAN)).thenReturn(Optional.of(van()));
        when(vehicles.save(any())).thenAnswer(call -> call.getArgument(0));
        when(vehicles.findAllVehicles()).thenReturn(List.of(van()));

        SameDayVehicleConfig updated = service.updateVehicle(VehicleType.VAN, "Small van", new BigDecimal("80"),
                new BigDecimal("150"), new BigDecimal("1.6"), false);

        assertThat(updated.label()).isEqualTo("Small van");
        assertThat(updated.active()).isFalse();
        assertThat(updated.limits().maxDistanceKm()).isEqualByComparingTo("150");
        assertThat(service.sameDayVehicles()).hasSize(1);
    }

    @Test
    void refusesAnUnknownVehicleOrImpossibleLimits() {
        when(vehicles.findByType(VehicleType.BIKE)).thenReturn(Optional.empty());
        when(vehicles.findByType(VehicleType.VAN)).thenReturn(Optional.of(van()));

        assertThatThrownBy(() -> service.updateVehicle(VehicleType.BIKE, "Bike", BigDecimal.ONE, BigDecimal.ONE,
                BigDecimal.ONE, true)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.updateVehicle(VehicleType.VAN, "Van", BigDecimal.ZERO, BigDecimal.ONE,
                BigDecimal.ONE, true)).satisfies(error -> assertCode(error, "INVALID_SAME_DAY_VEHICLE"));
        assertThatThrownBy(() -> service.updateVehicle(VehicleType.VAN, " ", BigDecimal.ONE, BigDecimal.ONE,
                BigDecimal.ONE, true)).satisfies(error -> assertCode(error, "INVALID_SAME_DAY_VEHICLE"));
        verify(vehicles, never()).save(any());
    }
}
