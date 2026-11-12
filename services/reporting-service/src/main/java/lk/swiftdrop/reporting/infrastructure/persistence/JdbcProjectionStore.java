package lk.swiftdrop.reporting.infrastructure.persistence;

import lk.swiftdrop.reporting.application.port.out.ProjectionStore;
import lk.swiftdrop.reporting.domain.model.ZoneCounts;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Plain SQL rather than JPA: the projection is upserts and set-based rebuilds, which SQL states
 * directly. Runs on the JPA transaction's connection, so it commits together with the
 * idempotency marker.
 */
@Repository
class JdbcProjectionStore implements ProjectionStore {

    /** First key of the two-part advisory locks, so this service's locks cannot collide with each other. */
    private static final int PARCEL_LOCK = 1;
    private static final int DAY_LOCK = 2;

    private final JdbcClient jdbc;

    JdbcProjectionStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void lockParcel(UUID parcelId) {
        jdbc.sql("SELECT pg_advisory_xact_lock(:ns, hashtext(:key))")
                .param("ns", PARCEL_LOCK)
                .param("key", parcelId.toString())
                .query().listOfRows();
    }

    @Override
    public void upsertCreated(CreatedFact fact) {
        jdbc.sql("""
                        INSERT INTO parcel_fact (parcel_id, tracking_number, zone_code, source, merchant_id,
                                                 created_at, created_date)
                        VALUES (:parcelId, :trackingNumber, :zoneCode, :source, :merchantId, :createdAt, :createdDate)
                        ON CONFLICT (parcel_id) DO UPDATE SET
                            tracking_number = EXCLUDED.tracking_number,
                            zone_code       = EXCLUDED.zone_code,
                            source          = EXCLUDED.source,
                            merchant_id     = EXCLUDED.merchant_id,
                            created_at      = EXCLUDED.created_at,
                            created_date    = EXCLUDED.created_date,
                            updated_at      = now()
                        """)
                .param("parcelId", fact.parcelId())
                .param("trackingNumber", fact.trackingNumber())
                .param("zoneCode", fact.zoneCode())
                .param("source", fact.source())
                .param("merchantId", fact.merchantId())
                .param("createdAt", utc(fact.createdAt()))
                .param("createdDate", fact.createdDate())
                .update();
    }

    @Override
    public void upsertAssignment(AssignmentFact fact) {
        jdbc.sql("""
                        INSERT INTO parcel_fact (parcel_id, tracking_number, courier_id, courier_assigned_at)
                        VALUES (:parcelId, :trackingNumber, :courierId, :assignedAt)
                        ON CONFLICT (parcel_id) DO UPDATE SET
                            tracking_number     = COALESCE(parcel_fact.tracking_number, EXCLUDED.tracking_number),
                            courier_id          = CASE
                                                      WHEN parcel_fact.courier_assigned_at IS NULL
                                                        OR EXCLUDED.courier_assigned_at >= parcel_fact.courier_assigned_at
                                                      THEN EXCLUDED.courier_id
                                                      ELSE parcel_fact.courier_id
                                                  END,
                            courier_assigned_at = GREATEST(parcel_fact.courier_assigned_at, EXCLUDED.courier_assigned_at),
                            updated_at          = now()
                        """)
                .param("parcelId", fact.parcelId())
                .param("trackingNumber", fact.trackingNumber())
                .param("courierId", fact.courierId())
                .param("assignedAt", utc(fact.assignedAt()))
                .update();
    }

    @Override
    public void insertTransition(TransitionFact fact) {
        jdbc.sql("""
                        INSERT INTO status_transition (event_id, parcel_id, from_status, to_status, courier_id,
                                                       failure_reason, changed_at, business_date)
                        VALUES (:eventId, :parcelId, :fromStatus, :toStatus, :courierId,
                                :failureReason, :changedAt, :businessDate)
                        ON CONFLICT (event_id) DO NOTHING
                        """)
                .param("eventId", fact.eventId())
                .param("parcelId", fact.parcelId())
                .param("fromStatus", fact.fromStatus())
                .param("toStatus", fact.toStatus())
                .param("courierId", fact.courierId())
                .param("failureReason", fact.failureReason())
                .param("changedAt", utc(fact.changedAt()))
                .param("businessDate", fact.businessDate())
                .update();
    }

    @Override
    public SortedSet<LocalDate> daysTouchedBy(UUID parcelId) {
        return new TreeSet<>(jdbc.sql("""
                        SELECT created_date AS day FROM parcel_fact
                         WHERE parcel_id = :parcelId AND created_date IS NOT NULL
                        UNION
                        SELECT business_date AS day FROM status_transition
                         WHERE parcel_id = :parcelId
                        """)
                .param("parcelId", parcelId)
                .query((rs, row) -> rs.getObject("day", LocalDate.class))
                .list());
    }

    @Override
    public void recomputeDay(LocalDate day) {
        jdbc.sql("SELECT pg_advisory_xact_lock(:ns, :key)")
                .param("ns", DAY_LOCK)
                .param("key", Math.toIntExact(day.toEpochDay()))
                .query().listOfRows();

        jdbc.sql("DELETE FROM daily_delivery_stats WHERE business_date = :day").param("day", day).update();
        jdbc.sql("""
                        INSERT INTO daily_delivery_stats (business_date, zone_code, created, delivered, failed, cancelled)
                        SELECT CAST(:day AS DATE), zone_code, SUM(created), SUM(delivered), SUM(failed), SUM(cancelled)
                          FROM (SELECT COALESCE(zone_code, :unknown) AS zone_code,
                                       1 AS created, 0 AS delivered, 0 AS failed, 0 AS cancelled
                                  FROM parcel_fact
                                 WHERE created_date = :day
                                UNION ALL
                                SELECT COALESCE(p.zone_code, :unknown),
                                       0,
                                       CASE WHEN t.to_status = 'DELIVERED' THEN 1 ELSE 0 END,
                                       CASE WHEN t.to_status = 'FAILED' THEN 1 ELSE 0 END,
                                       CASE WHEN t.to_status = 'CANCELLED' THEN 1 ELSE 0 END
                                  FROM status_transition t
                                  LEFT JOIN parcel_fact p ON p.parcel_id = t.parcel_id
                                 WHERE t.business_date = :day
                                   AND t.to_status IN ('DELIVERED', 'FAILED', 'CANCELLED')) activity
                         GROUP BY zone_code
                        """)
                .param("day", day)
                .param("unknown", ZoneCounts.UNKNOWN_ZONE)
                .update();

        jdbc.sql("DELETE FROM courier_daily_stats WHERE business_date = :day").param("day", day).update();
        jdbc.sql("""
                        INSERT INTO courier_daily_stats (business_date, courier_id, delivered, failed)
                        SELECT CAST(:day AS DATE), COALESCE(t.courier_id, p.courier_id),
                               COUNT(*) FILTER (WHERE t.to_status = 'DELIVERED'),
                               COUNT(*) FILTER (WHERE t.to_status = 'FAILED')
                          FROM status_transition t
                          LEFT JOIN parcel_fact p ON p.parcel_id = t.parcel_id
                         WHERE t.business_date = :day
                           AND t.to_status IN ('DELIVERED', 'FAILED')
                           AND COALESCE(t.courier_id, p.courier_id) IS NOT NULL
                         GROUP BY COALESCE(t.courier_id, p.courier_id)
                        """)
                .param("day", day)
                .update();
    }

    /** The Postgres driver binds OffsetDateTime to timestamptz; it has no mapping for Instant. */
    private static OffsetDateTime utc(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }
}
