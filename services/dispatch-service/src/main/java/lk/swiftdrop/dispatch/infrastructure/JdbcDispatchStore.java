package lk.swiftdrop.dispatch.infrastructure;

import lk.swiftdrop.dispatch.application.*;
import lk.swiftdrop.common.web.error.ResourceNotFoundException;
import lk.swiftdrop.events.parcel.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import java.sql.*;
import java.time.*;
import java.util.*;
import static lk.swiftdrop.dispatch.application.DispatchModels.*;

@Repository
public class JdbcDispatchStore implements DispatchStore {
    private final JdbcClient db;
    public JdbcDispatchStore(JdbcClient db) { this.db = db; }
    private static final String SELECT = "SELECT p.*, a.id assignment_id, a.courier_id, a.run_date FROM parcel_summary p LEFT JOIN assignment a ON a.parcel_id=p.parcel_id ";
    private static Parcel map(ResultSet r, int row) throws SQLException {
        var created = r.getTimestamp("created_at");
        return new Parcel(r.getObject("parcel_id", UUID.class), r.getString("tracking_number"), r.getString("zone"),
            r.getString("size_class"), r.getString("pickup_city"), r.getString("dropoff_city"),
            created == null ? null : created.toInstant(), r.getString("status"), r.getObject("assignment_id", UUID.class),
            r.getString("courier_id"), r.getObject("run_date", LocalDate.class));
    }
    public void created(ParcelCreated e) {
        db.sql("""
            INSERT INTO parcel_summary(parcel_id, tracking_number, zone, size_class, pickup_city, dropoff_city, created_at, booked)
            VALUES (:id,:tracking,:zone,:size,:pickup,:dropoff,:created,true)
            ON CONFLICT(parcel_id) DO UPDATE SET tracking_number=excluded.tracking_number, zone=excluded.zone,
             size_class=excluded.size_class, pickup_city=excluded.pickup_city, dropoff_city=excluded.dropoff_city,
             created_at=excluded.created_at, booked=true WHERE NOT parcel_summary.booked
            """).param("id", e.parcelId()).param("tracking", e.trackingNumber()).param("zone", e.dropoff().zoneCode())
            .param("size", e.sizeClass().name()).param("pickup", e.pickup().city()).param("dropoff", e.dropoff().city())
            .param("created", Timestamp.from(e.createdAt())).update();
    }
    public void status(ParcelStatusChanged e) {
        // Status can arrive before creation because these facts are on different Kafka topics.
        db.sql("""
            INSERT INTO parcel_summary(parcel_id, status, status_at) VALUES (:id,:status,:at)
            ON CONFLICT(parcel_id) DO UPDATE SET status=excluded.status, status_at=excluded.status_at
            WHERE parcel_summary.status_at IS NULL OR parcel_summary.status_at < excluded.status_at
            """).param("id", e.parcelId()).param("status", e.toStatus().name()).param("at", Timestamp.from(e.changedAt())).update();
    }
    public Parcel lock(UUID id) {
        db.sql("SELECT parcel_id FROM parcel_summary WHERE parcel_id=:id FOR UPDATE").param("id", id)
            .query(UUID.class).optional().orElseThrow(() -> ResourceNotFoundException.of("parcel", id));
        // A separate statement gets a fresh READ COMMITTED snapshot after waiting for a competing allocation.
        return db.sql(SELECT + "WHERE p.parcel_id=:id AND p.booked").param("id", id)
            .query(JdbcDispatchStore::map).optional().orElseThrow(() -> ResourceNotFoundException.of("parcel", id));
    }
    public Parcel lockAssignment(UUID id) {
        UUID parcelId = db.sql("SELECT parcel_id FROM assignment WHERE id=:id").param("id", id)
            .query(UUID.class).optional().orElseThrow(() -> ResourceNotFoundException.of("assignment", id));
        return lock(parcelId);
    }
    public Page unassigned(String zone, int page, int size) {
        String where = "WHERE p.booked AND a.id IS NULL AND p.status NOT IN ('DELIVERED','FAILED','CANCELLED') AND (:zone IS NULL OR p.zone=:zone) ";
        var rows = db.sql(SELECT + where + "ORDER BY p.created_at, p.parcel_id LIMIT :size OFFSET :offset")
            .param("zone", zone, Types.VARCHAR).param("size", size).param("offset", (long) page * size).query(JdbcDispatchStore::map).list();
        long count = db.sql("SELECT count(*) FROM parcel_summary p LEFT JOIN assignment a ON a.parcel_id=p.parcel_id " + where)
            .param("zone", zone, Types.VARCHAR).query(Long.class).single();
        return new Page(rows, count, page, size);
    }
    public List<Parcel> assignments(LocalDate date, String courier) {
        return db.sql(SELECT + "WHERE a.run_date=:date AND (:courier IS NULL OR a.courier_id=:courier) ORDER BY a.assigned_at, p.parcel_id")
            .param("date", date).param("courier", courier, Types.VARCHAR).query(JdbcDispatchStore::map).list();
    }
    public Map<String, Counts> counts(LocalDate today, ZoneId zone) {
        Map<String, Counts> result = new HashMap<>();
        db.sql("""
            SELECT courier_id,
             count(*) FILTER (WHERE status NOT IN ('DELIVERED','FAILED','CANCELLED')) active_count,
             count(*) FILTER (WHERE status='DELIVERED' AND status_at >= :start AND status_at < :end) delivered_today,
             count(*) FILTER (WHERE status='FAILED' AND status_at >= :start AND status_at < :end) failed_today
            FROM courier_workload GROUP BY courier_id
            """).param("start", Timestamp.from(today.atStartOfDay(zone).toInstant()))
            .param("end", Timestamp.from(today.plusDays(1).atStartOfDay(zone).toInstant()))
            .query((r, n) -> { result.put(r.getString("courier_id"), new Counts(r.getLong("active_count"), r.getLong("delivered_today"), r.getLong("failed_today"))); return r.getString("courier_id"); }).list();
        return result;
    }
    public Allocation save(Parcel p, String courier, LocalDate date, String actor, Instant now) {
        UUID id = p.assignmentId() == null ? UUID.randomUUID() : p.assignmentId();
        db.sql("""
            INSERT INTO assignment(id,parcel_id,courier_id,previous_courier_id,run_date,assigned_by,assigned_at)
            VALUES (:id,:parcel,:courier,:previous,:date,:actor,:now)
            ON CONFLICT(parcel_id) DO UPDATE SET courier_id=excluded.courier_id, previous_courier_id=assignment.courier_id,
             run_date=excluded.run_date, assigned_by=excluded.assigned_by, assigned_at=excluded.assigned_at
            """).param("id", id).param("parcel", p.parcelId()).param("courier", courier).param("previous", p.courierId(), Types.VARCHAR)
            .param("date", date).param("actor", actor).param("now", Timestamp.from(now)).update();
        return new Allocation(id, p.parcelId(), courier, p.courierId(), date, now);
    }
}
