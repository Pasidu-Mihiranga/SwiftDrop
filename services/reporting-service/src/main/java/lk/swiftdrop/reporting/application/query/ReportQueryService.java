package lk.swiftdrop.reporting.application.query;

import lk.swiftdrop.reporting.application.port.out.ReportStore;
import lk.swiftdrop.reporting.domain.model.CourierPerformance;
import lk.swiftdrop.reporting.domain.model.DailyCounts;
import lk.swiftdrop.reporting.domain.model.DeliveryCounts;
import lk.swiftdrop.reporting.domain.model.ReportPeriod;
import lk.swiftdrop.reporting.domain.model.ZoneCounts;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/** Answers the report endpoints from the daily rollups. */
@Service
@Transactional(readOnly = true)
public class ReportQueryService {

    private final ReportStore store;

    public ReportQueryService(ReportStore store) {
        this.store = store;
    }

    /** One entry per day of the period, with zeros for days without activity, so charts have no gaps. */
    public List<DailyCounts> daily(ReportPeriod period, Optional<String> zoneCode) {
        Map<LocalDate, DeliveryCounts> byDay = store.dailyTotals(period, zoneCode).stream()
                .collect(Collectors.toMap(DailyCounts::date, DailyCounts::counts));
        return period.days().stream()
                .map(day -> new DailyCounts(day, byDay.getOrDefault(day, DeliveryCounts.ZERO)))
                .toList();
    }

    /** Sum over the period, across all zones or for one zone. */
    public DeliveryCounts totals(ReportPeriod period, Optional<String> zoneCode) {
        return store.dailyTotals(period, zoneCode).stream()
                .map(DailyCounts::counts)
                .reduce(DeliveryCounts.ZERO, DeliveryCounts::plus);
    }

    public List<ZoneCounts> zones(ReportPeriod period) {
        return store.zoneTotals(period);
    }

    public List<CourierPerformance> couriers(ReportPeriod period) {
        return store.courierTotals(period);
    }
}
