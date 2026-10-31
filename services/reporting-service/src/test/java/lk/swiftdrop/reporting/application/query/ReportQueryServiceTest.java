package lk.swiftdrop.reporting.application.query;

import lk.swiftdrop.reporting.application.port.out.ReportStore;
import lk.swiftdrop.reporting.domain.model.CourierPerformance;
import lk.swiftdrop.reporting.domain.model.DailyCounts;
import lk.swiftdrop.reporting.domain.model.DeliveryCounts;
import lk.swiftdrop.reporting.domain.model.ReportPeriod;
import lk.swiftdrop.reporting.domain.model.ZoneCounts;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReportQueryServiceTest {

    private static final LocalDate OCT_1 = LocalDate.of(2026, 10, 1);
    private static final ReportPeriod THREE_DAYS = new ReportPeriod(OCT_1, OCT_1.plusDays(2));

    private final ReportStore store = mock(ReportStore.class);
    private final ReportQueryService service = new ReportQueryService(store);

    @Test
    void dailyFillsDaysWithoutActivityWithZeros() {
        when(store.dailyTotals(THREE_DAYS, Optional.empty()))
                .thenReturn(List.of(new DailyCounts(OCT_1.plusDays(1), new DeliveryCounts(5, 4, 1, 0))));

        assertThat(service.daily(THREE_DAYS, Optional.empty())).containsExactly(
                new DailyCounts(OCT_1, DeliveryCounts.ZERO),
                new DailyCounts(OCT_1.plusDays(1), new DeliveryCounts(5, 4, 1, 0)),
                new DailyCounts(OCT_1.plusDays(2), DeliveryCounts.ZERO));
    }

    @Test
    void totalsSumTheDays() {
        when(store.dailyTotals(THREE_DAYS, Optional.of("CP-KDY"))).thenReturn(List.of(
                new DailyCounts(OCT_1, new DeliveryCounts(2, 1, 0, 0)),
                new DailyCounts(OCT_1.plusDays(2), new DeliveryCounts(1, 2, 1, 1))));

        assertThat(service.totals(THREE_DAYS, Optional.of("CP-KDY"))).isEqualTo(new DeliveryCounts(3, 3, 1, 1));
    }

    @Test
    void zonesAndCouriersComeStraightFromTheStore() {
        List<ZoneCounts> zones = List.of(new ZoneCounts("CP-KDY", new DeliveryCounts(1, 1, 0, 0)));
        List<CourierPerformance> couriers = List.of(new CourierPerformance("c-1", 4, 1));
        when(store.zoneTotals(THREE_DAYS)).thenReturn(zones);
        when(store.courierTotals(THREE_DAYS)).thenReturn(couriers);

        assertThat(service.zones(THREE_DAYS)).isEqualTo(zones);
        assertThat(service.couriers(THREE_DAYS)).isEqualTo(couriers);
    }
}
