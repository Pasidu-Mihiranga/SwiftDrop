package lk.swiftdrop.reporting.api.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Pattern;
import lk.swiftdrop.common.web.error.BadRequestException;
import lk.swiftdrop.reporting.api.dto.ReportResponses.CourierReport;
import lk.swiftdrop.reporting.api.dto.ReportResponses.DailyReport;
import lk.swiftdrop.reporting.api.dto.ReportResponses.Day;
import lk.swiftdrop.reporting.api.dto.ReportResponses.SuccessRateReport;
import lk.swiftdrop.reporting.api.dto.ReportResponses.Totals;
import lk.swiftdrop.reporting.api.dto.ReportResponses.ZoneReport;
import lk.swiftdrop.reporting.application.query.ReportQueryService;
import lk.swiftdrop.reporting.domain.exception.InvalidReportPeriodException;
import lk.swiftdrop.reporting.domain.model.DailyCounts;
import lk.swiftdrop.reporting.domain.model.DeliveryCounts;
import lk.swiftdrop.reporting.domain.model.ReportPeriod;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Delivery statistics for operations staff. Dates are business days in the operator's time zone. */
@RestController
@RequestMapping("/api/v1/reports")
@PreAuthorize("hasAnyRole('DISPATCHER', 'ADMIN')")
@Tag(name = "Reports", description = "Delivery statistics (DISPATCHER, ADMIN)")
public class ReportController {

    private static final String ZONE_PATTERN = "[A-Z0-9-]{1,32}";

    private final ReportQueryService reports;

    public ReportController(ReportQueryService reports) {
        this.reports = reports;
    }

    @GetMapping("/daily")
    @Operation(summary = "Created / delivered / failed / cancelled per day, optionally for one zone")
    public DailyReport daily(@RequestParam @DateTimeFormat(iso = ISO.DATE) LocalDate from,
                             @RequestParam @DateTimeFormat(iso = ISO.DATE) LocalDate to,
                             @RequestParam(required = false) @Pattern(regexp = ZONE_PATTERN) String zone) {
        List<DailyCounts> days = reports.daily(period(from, to), Optional.ofNullable(zone));
        DeliveryCounts totals = days.stream().map(DailyCounts::counts).reduce(DeliveryCounts.ZERO, DeliveryCounts::plus);
        return new DailyReport(from, to, zone, Totals.of(totals), days.stream().map(Day::of).toList());
    }

    @GetMapping("/success-rate")
    @Operation(summary = "Delivered vs failed attempts for a period, optionally for one zone")
    public SuccessRateReport successRate(@RequestParam @DateTimeFormat(iso = ISO.DATE) LocalDate from,
                                         @RequestParam @DateTimeFormat(iso = ISO.DATE) LocalDate to,
                                         @RequestParam(required = false) @Pattern(regexp = ZONE_PATTERN) String zone) {
        DeliveryCounts totals = reports.totals(period(from, to), Optional.ofNullable(zone));
        return new SuccessRateReport(from, to, zone, totals.delivered(), totals.failed(), totals.successRate().rate());
    }

    @GetMapping("/zones")
    @Operation(summary = "Totals per dropoff zone for a period")
    public List<ZoneReport> zones(@RequestParam @DateTimeFormat(iso = ISO.DATE) LocalDate from,
                                  @RequestParam @DateTimeFormat(iso = ISO.DATE) LocalDate to) {
        return reports.zones(period(from, to)).stream().map(ZoneReport::of).toList();
    }

    @GetMapping("/couriers")
    @Operation(summary = "Delivered and failed attempts per courier for a period, most deliveries first")
    public List<CourierReport> couriers(@RequestParam @DateTimeFormat(iso = ISO.DATE) LocalDate from,
                                        @RequestParam @DateTimeFormat(iso = ISO.DATE) LocalDate to) {
        return reports.couriers(period(from, to)).stream().map(CourierReport::of).toList();
    }

    private static ReportPeriod period(LocalDate from, LocalDate to) {
        try {
            return new ReportPeriod(from, to);
        } catch (InvalidReportPeriodException ex) {
            throw new BadRequestException("INVALID_REPORT_PERIOD", ex.getMessage());
        }
    }
}
