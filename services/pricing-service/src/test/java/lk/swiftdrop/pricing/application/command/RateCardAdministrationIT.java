package lk.swiftdrop.pricing.application.command;

import lk.swiftdrop.common.web.error.ConflictException;
import lk.swiftdrop.pricing.application.model.Paged;
import lk.swiftdrop.pricing.application.model.RateCardFilter;
import lk.swiftdrop.pricing.application.query.RateCardQueryService;
import lk.swiftdrop.pricing.application.query.RateCardSelectionService;
import lk.swiftdrop.pricing.domain.model.RateCard;
import lk.swiftdrop.pricing.domain.model.ZoneCode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises rate-card administration against the seeded database: a price change has to close the
 * card in force and add a new one, and quoting has to pick the right card on either side of the
 * changeover date.
 */
@Testcontainers
@SpringBootTest
@Transactional
class RateCardAdministrationIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16.15-alpine");
    @Container
    @ServiceConnection(name = "redis")
    static final GenericContainer<?> REDIS =
            new GenericContainer<>("redis:7.4.11-alpine").withExposedPorts(6379);

    private static final ZoneCode WESTERN = ZoneCode.of("WP");
    private static final ZoneCode CENTRAL = ZoneCode.of("CP");
    private static final LocalDate BEFORE = LocalDate.of(2026, 6, 1);
    private static final LocalDate CHANGEOVER = LocalDate.of(2026, 7, 1);

    @Autowired
    private RateCardAdminService commands;
    @Autowired
    private RateCardQueryService queries;
    @Autowired
    private RateCardSelectionService selection;

    @Test
    void theSeededMatrixIsPagedWithTotalsAcrossAllEightyOneLanes() {
        Paged<RateCard> first = queries.list(RateCardFilter.unfiltered(), 0, 20);

        assertThat(first.content()).hasSize(20);
        assertThat(first.totalElements()).isEqualTo(81);
        assertThat(first.totalPages()).isEqualTo(5);
        assertThat(queries.list(RateCardFilter.unfiltered(), 4, 20).content()).hasSize(1);
    }

    @Test
    void filtersNarrowTheListingToOneLane() {
        Paged<RateCard> lane = queries.list(new RateCardFilter(WESTERN, CENTRAL, null), 0, 20);

        assertThat(lane.totalElements()).isEqualTo(1);
        assertThat(lane.content().getFirst().fromZone()).isEqualTo(WESTERN);
        assertThat(lane.content().getFirst().toZone()).isEqualTo(CENTRAL);
    }

    @Test
    void theActiveOnFilterExcludesCardsThatHaveNotStartedYet() {
        assertThat(queries.list(new RateCardFilter(null, null, LocalDate.of(2025, 12, 31)), 0, 100)
                .totalElements()).isZero();
        assertThat(queries.list(new RateCardFilter(null, null, BEFORE), 0, 100)
                .totalElements()).isEqualTo(81);
    }

    @Test
    void refusesANewCardWhileOneIsStillOpenForTheSameLane() {
        assertThatThrownBy(() -> commands.create(WESTERN, CENTRAL, new BigDecimal("600.00"),
                new BigDecimal("95.00"), new BigDecimal("1.500"), new BigDecimal("0.0150"),
                CHANGEOVER, null))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void aPriceChangeClosesTheOldCardAndTakesEffectOnTheChangeoverDate() {
        RateCard current = selection.select(WESTERN, CENTRAL, BEFORE);
        BigDecimal oldBaseFee = current.baseFee();

        commands.close(current.id(), CHANGEOVER);
        RateCard replacement = commands.create(WESTERN, CENTRAL, new BigDecimal("600.00"),
                new BigDecimal("95.00"), new BigDecimal("1.500"), new BigDecimal("0.0150"),
                CHANGEOVER, null);

        assertThat(selection.select(WESTERN, CENTRAL, BEFORE).baseFee()).isEqualByComparingTo(oldBaseFee);
        assertThat(selection.select(WESTERN, CENTRAL, CHANGEOVER).baseFee()).isEqualByComparingTo("600.00");
        assertThat(selection.select(WESTERN, CENTRAL, CHANGEOVER).id()).isEqualTo(replacement.id());

        // The superseded card is history, not deleted, so an issued quote stays auditable.
        assertThat(queries.find(current.id()).effectiveTo()).isEqualTo(CHANGEOVER);
        List<RateCard> lane = queries.list(new RateCardFilter(WESTERN, CENTRAL, null), 0, 20).content();
        assertThat(lane).hasSize(2);
    }

    @Test
    void closingTheOnlyCardLeavesTheLaneUnquotable() {
        RateCard current = selection.select(WESTERN, CENTRAL, BEFORE);

        commands.close(current.id(), CHANGEOVER);

        assertThat(selection.select(WESTERN, CENTRAL, BEFORE)).isNotNull();
        assertThatThrownBy(() -> selection.select(WESTERN, CENTRAL, CHANGEOVER))
                .hasMessageContaining("No rate card is in force");
    }
}
