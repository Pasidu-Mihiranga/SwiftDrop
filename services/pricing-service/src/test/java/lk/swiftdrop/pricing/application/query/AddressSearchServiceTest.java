package lk.swiftdrop.pricing.application.query;

import lk.swiftdrop.common.web.error.ApiException;
import lk.swiftdrop.pricing.application.port.out.AddressGeocoder;
import lk.swiftdrop.pricing.application.port.out.AddressGeocoder.Match;
import lk.swiftdrop.pricing.application.query.AddressSearchService.Suggestion;
import lk.swiftdrop.pricing.application.query.CoverageQueryService.LocatedPoint;
import lk.swiftdrop.pricing.domain.model.GeoPoint;
import lk.swiftdrop.pricing.domain.model.ZoneCode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AddressSearchServiceTest {

    private static final GeoPoint COLOMBO = GeoPoint.of("6.91", "79.85");
    private static final GeoPoint CHENNAI = GeoPoint.of("13.08", "80.27");

    private final AddressGeocoder geocoder = mock(AddressGeocoder.class);
    private final CoverageQueryService coverage = mock(CoverageQueryService.class);
    private final AddressSearchService service = new AddressSearchService(Optional.of(geocoder), coverage);

    private static void assertCode(Throwable error, String code) {
        assertThat(error).isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo(code));
    }

    @Test
    void namesTheDistrictAQuoteWillUseAndDropsPlacesOutsideTheServiceArea() {
        when(geocoder.search("45 Galle Road", 10)).thenReturn(List.of(
                new Match(" 45, Galle Road, Colombo 03 ", COLOMBO),
                new Match("Galle Road, Chennai", CHENNAI),
                new Match("45, galle road, colombo 03", COLOMBO)));
        when(coverage.locate(COLOMBO)).thenReturn(Optional.of(new LocatedPoint("Colombo", ZoneCode.of("WP"))));
        when(coverage.locate(CHENNAI)).thenReturn(Optional.empty());

        List<Suggestion> suggestions = service.search("  45   Galle Road ");

        assertThat(suggestions).containsExactly(new Suggestion("45, Galle Road, Colombo 03", COLOMBO, "Colombo", "WP"));
    }

    @Test
    void showsAtMostFiveSuggestions() {
        when(geocoder.search(anyString(), anyInt())).thenReturn(IntStream.range(0, 10)
                .mapToObj(i -> new Match("Place " + i, COLOMBO)).toList());
        when(coverage.locate(any())).thenReturn(Optional.of(new LocatedPoint("Colombo", ZoneCode.of("WP"))));

        assertThat(service.search("Place")).hasSize(5);
    }

    @Test
    void refusesAQueryTooShortOrTooLongBeforeAskingTheGeocoder() {
        for (String query : new String[] {null, "", "  ab ", "x".repeat(121)}) {
            assertThatThrownBy(() -> service.search(query)).satisfies(error -> assertCode(error, "INVALID_ADDRESS_QUERY"));
        }
        verifyNoInteractions(geocoder);
    }

    @Test
    void saysSearchIsUnavailableWhenNoGeocoderIsConfigured() {
        AddressSearchService off = new AddressSearchService(Optional.empty(), coverage);

        assertThatThrownBy(() -> off.search("Kandy")).satisfies(error -> {
            assertCode(error, "ADDRESS_SEARCH_UNAVAILABLE");
            assertThat(((ApiException) error).status().value()).isEqualTo(503);
        });
    }

    @Test
    void passesOnAGeocoderFailure() {
        when(geocoder.search(anyString(), anyInt())).thenThrow(new AddressSearchUnavailableException("down",
                new IllegalStateException("cause")));

        assertThatThrownBy(() -> service.search("Kandy")).satisfies(error -> {
            assertCode(error, "ADDRESS_SEARCH_UNAVAILABLE");
            assertThat(error.getCause()).isInstanceOf(IllegalStateException.class);
        });
        verify(geocoder).search("Kandy", 10);
    }
}
