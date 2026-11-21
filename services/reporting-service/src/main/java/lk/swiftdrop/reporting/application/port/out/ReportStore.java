package lk.swiftdrop.reporting.application.port.out;

import lk.swiftdrop.reporting.domain.model.CourierPerformance;
import lk.swiftdrop.reporting.domain.model.DailyCounts;
import lk.swiftdrop.reporting.domain.model.ReportPeriod;
import lk.swiftdrop.reporting.domain.model.ZoneCounts;

import java.util.List;
import java.util.Optional;

/** Read side of the rollups. Days without any activity are simply absent from the results. */
public interface ReportStore {

    /** Totals per day, across all zones or for one zone. */
    List<DailyCounts> dailyTotals(ReportPeriod period, Optional<String> zoneCode);

    /** Totals per zone over the whole period, ordered by zone code. */
    List<ZoneCounts> zoneTotals(ReportPeriod period);

    /** Totals per courier over the whole period, most deliveries first. */
    List<CourierPerformance> courierTotals(ReportPeriod period);
}
