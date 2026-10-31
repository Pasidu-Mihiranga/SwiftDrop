package lk.swiftdrop.reporting.api.dto;

import lk.swiftdrop.reporting.domain.model.CourierPerformance;
import lk.swiftdrop.reporting.domain.model.DailyCounts;
import lk.swiftdrop.reporting.domain.model.DeliveryCounts;
import lk.swiftdrop.reporting.domain.model.ZoneCounts;

import java.time.LocalDate;
import java.util.List;

/**
 * Response bodies of the report endpoints. {@code successRate} is delivered / (delivered + failed),
 * between 0 and 1, or null when there were no delivery attempts in the period.
 */
public final class ReportResponses {

    private ReportResponses() {
    }

    public record Totals(long created, long delivered, long failed, long cancelled, Double successRate) {
        public static Totals of(DeliveryCounts counts) {
            return new Totals(counts.created(), counts.delivered(), counts.failed(), counts.cancelled(),
                    counts.successRate().rate());
        }
    }

    public record Day(LocalDate date, long created, long delivered, long failed, long cancelled) {
        public static Day of(DailyCounts day) {
            DeliveryCounts c = day.counts();
            return new Day(day.date(), c.created(), c.delivered(), c.failed(), c.cancelled());
        }
    }

    public record DailyReport(LocalDate from, LocalDate to, String zone, Totals totals, List<Day> days) {
    }

    public record SuccessRateReport(LocalDate from, LocalDate to, String zone, long delivered, long failed,
                                    Double successRate) {
    }

    public record ZoneReport(String zoneCode, long created, long delivered, long failed, long cancelled,
                             Double successRate) {
        public static ZoneReport of(ZoneCounts zone) {
            DeliveryCounts c = zone.counts();
            return new ZoneReport(zone.zoneCode(), c.created(), c.delivered(), c.failed(), c.cancelled(),
                    c.successRate().rate());
        }
    }

    public record CourierReport(String courierId, long delivered, long failed, Double successRate) {
        public static CourierReport of(CourierPerformance courier) {
            return new CourierReport(courier.courierId(), courier.delivered(), courier.failed(),
                    courier.successRate().rate());
        }
    }
}
