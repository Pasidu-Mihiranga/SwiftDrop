package lk.swiftdrop.reporting.infrastructure.persistence;

import lk.swiftdrop.reporting.application.port.out.ReportStore;
import lk.swiftdrop.reporting.domain.model.CourierPerformance;
import lk.swiftdrop.reporting.domain.model.DailyCounts;
import lk.swiftdrop.reporting.domain.model.DeliveryCounts;
import lk.swiftdrop.reporting.domain.model.ReportPeriod;
import lk.swiftdrop.reporting.domain.model.ZoneCounts;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
class JdbcReportStore implements ReportStore {

    private static final String SUMS =
            "SUM(created) AS created, SUM(delivered) AS delivered, SUM(failed) AS failed, SUM(cancelled) AS cancelled";

    private final JdbcClient jdbc;

    JdbcReportStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<DailyCounts> dailyTotals(ReportPeriod period, Optional<String> zoneCode) {
        String zoneFilter = zoneCode.isPresent() ? " AND zone_code = :zone" : "";
        JdbcClient.StatementSpec statement = jdbc.sql("SELECT business_date, " + SUMS
                        + " FROM daily_delivery_stats WHERE business_date BETWEEN :from AND :to" + zoneFilter
                        + " GROUP BY business_date ORDER BY business_date")
                .param("from", period.from())
                .param("to", period.to());
        if (zoneCode.isPresent()) {
            statement = statement.param("zone", zoneCode.get());
        }
        return statement
                .query((rs, row) -> new DailyCounts(rs.getObject("business_date", LocalDate.class), counts(rs)))
                .list();
    }

    @Override
    public List<ZoneCounts> zoneTotals(ReportPeriod period) {
        return jdbc.sql("SELECT zone_code, " + SUMS
                        + " FROM daily_delivery_stats WHERE business_date BETWEEN :from AND :to"
                        + " GROUP BY zone_code ORDER BY zone_code")
                .param("from", period.from())
                .param("to", period.to())
                .query((rs, row) -> new ZoneCounts(rs.getString("zone_code"), counts(rs)))
                .list();
    }

    @Override
    public List<CourierPerformance> courierTotals(ReportPeriod period) {
        return jdbc.sql("""
                        SELECT courier_id, SUM(delivered) AS delivered, SUM(failed) AS failed
                          FROM courier_daily_stats
                         WHERE business_date BETWEEN :from AND :to
                         GROUP BY courier_id
                         ORDER BY delivered DESC, courier_id
                        """)
                .param("from", period.from())
                .param("to", period.to())
                .query((rs, row) -> new CourierPerformance(rs.getString("courier_id"),
                        rs.getLong("delivered"), rs.getLong("failed")))
                .list();
    }

    private static DeliveryCounts counts(ResultSet rs) throws SQLException {
        return new DeliveryCounts(rs.getLong("created"), rs.getLong("delivered"),
                rs.getLong("failed"), rs.getLong("cancelled"));
    }
}
