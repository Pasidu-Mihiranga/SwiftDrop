package lk.swiftdrop.pricing.application.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PagedTest {

    @Test
    void countsAPartialPageAsAWholePage() {
        assertThat(new Paged<>(List.of(), 0, 20, 81).totalPages()).isEqualTo(5);
        assertThat(new Paged<>(List.of(), 0, 20, 80).totalPages()).isEqualTo(4);
        assertThat(new Paged<>(List.of(), 0, 20, 0).totalPages()).isZero();
    }

    @Test
    void treatsAnUnsetPageSizeAsNoPages() {
        assertThat(new Paged<>(List.of(), 0, 0, 81).totalPages()).isZero();
    }

    @Test
    void keepsItsContentImmutable() {
        assertThat(new Paged<>(List.of("a"), 0, 1, 1).content()).containsExactly("a");
    }

    @Test
    void anUnfilteredFilterNarrowsNothing() {
        RateCardFilter filter = RateCardFilter.unfiltered();

        assertThat(filter.fromZone()).isNull();
        assertThat(filter.toZone()).isNull();
        assertThat(filter.activeOn()).isNull();
    }
}
