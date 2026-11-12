package lk.swiftdrop.reporting.domain.model;

import lk.swiftdrop.reporting.domain.exception.InvalidReportPeriodException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReportModelTest {

    private static final LocalDate OCT_1 = LocalDate.of(2026, 10, 1);

    @Test
    void periodListsEveryDayInclusive() {
        assertThat(new ReportPeriod(OCT_1, OCT_1.plusDays(2)).days())
                .containsExactly(OCT_1, OCT_1.plusDays(1), OCT_1.plusDays(2));
        assertThat(new ReportPeriod(OCT_1, OCT_1).days()).containsExactly(OCT_1);
    }

    @Test
    void periodRejectsReversedRange() {
        assertThatThrownBy(() -> new ReportPeriod(OCT_1, OCT_1.minusDays(1)))
                .isInstanceOf(InvalidReportPeriodException.class)
                .hasMessageContaining("on or before");
    }

    @Test
    void periodIsCappedAtAYear() {
        assertThat(new ReportPeriod(OCT_1, OCT_1.plusDays(ReportPeriod.MAX_DAYS - 1)).days()).hasSize(ReportPeriod.MAX_DAYS);
        assertThatThrownBy(() -> new ReportPeriod(OCT_1, OCT_1.plusDays(ReportPeriod.MAX_DAYS)))
                .isInstanceOf(InvalidReportPeriodException.class);
    }

    @Test
    void successRateIsNullWithoutAttempts() {
        assertThat(new SuccessRate(0, 0).rate()).isNull();
        assertThat(new SuccessRate(9, 1).rate()).isEqualTo(0.9);
        assertThat(new SuccessRate(0, 4).rate()).isZero();
    }

    @Test
    void countsAddUpAndExposeTheirSuccessRate() {
        DeliveryCounts sum = new DeliveryCounts(3, 2, 1, 0).plus(new DeliveryCounts(1, 2, 0, 1));
        assertThat(sum).isEqualTo(new DeliveryCounts(4, 4, 1, 1));
        assertThat(sum.successRate().rate()).isEqualTo(0.8);
        assertThat(new CourierPerformance("c-1", 3, 1).successRate().rate()).isEqualTo(0.75);
    }

    @Test
    void businessDayFollowsTheOperatorsTimeZone() {
        BusinessCalendar colombo = new BusinessCalendar(ZoneId.of("Asia/Colombo"));
        // 19:30 UTC on the 20th is 01:00 on the 21st in Colombo (UTC+05:30).
        assertThat(colombo.dateOf(Instant.parse("2026-10-20T19:30:00Z"))).isEqualTo(LocalDate.of(2026, 10, 21));
        assertThat(colombo.dateOf(Instant.parse("2026-10-20T18:29:59Z"))).isEqualTo(LocalDate.of(2026, 10, 20));
    }
}
