package lk.swiftdrop.reporting.domain.model;

import java.time.LocalDate;

public record DailyCounts(LocalDate date, DeliveryCounts counts) {
}
