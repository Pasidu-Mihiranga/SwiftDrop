package lk.swiftdrop.parcel.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lk.swiftdrop.events.common.Types.ActorRole;
import lk.swiftdrop.events.common.Types.FailureReason;
import lk.swiftdrop.events.common.Types.Location;
import lk.swiftdrop.events.common.Types.ParcelStatus;

import java.time.Instant;
import java.util.UUID;

/** One append-only row of {@code parcel_status_history}. Rows are never updated or deleted. */
@Entity
@Table(name = "parcel_status_history")
public class ParcelStatusHistoryEntity {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "parcel_id", nullable = false, updatable = false)
    private ParcelEntity parcel;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 20, updatable = false)
    private ParcelStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 20, updatable = false)
    private ParcelStatus toStatus;

    @Column(name = "actor_id", nullable = false, length = 64, updatable = false)
    private String actorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_role", nullable = false, length = 20, updatable = false)
    private ActorRole actorRole;

    @Column(length = 500, updatable = false)
    private String note;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "failure_reason", length = 30, updatable = false)
    private FailureReason failureReason;

    @Column(name = "proof_type", length = 20, updatable = false)
    private String proofType;

    @Column(name = "proof_id", length = 64, updatable = false)
    private String proofId;

    @Column(name = "received_by", length = 100, updatable = false)
    private String receivedBy;

    @Column(name = "lat", precision = 9, scale = 6, updatable = false)
    private Double latitude;

    @Column(name = "lng", precision = 9, scale = 6, updatable = false)
    private Double longitude;

    @Column(name = "location_city", length = 120, updatable = false)
    private String locationCity;

    @Column(name = "location_district", length = 120, updatable = false)
    private String locationDistrict;

    @Column(name = "location_zone_code", length = 80, updatable = false)
    private String locationZoneCode;

    protected ParcelStatusHistoryEntity() {
        // for JPA
    }

    ParcelStatusHistoryEntity(ParcelEntity parcel, ParcelStatus fromStatus, ParcelStatus toStatus, String actorId,
                              ActorRole actorRole, String note, Instant occurredAt) {
        this.id = UUID.randomUUID();
        this.parcel = parcel;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.actorId = actorId;
        this.actorRole = actorRole;
        this.note = note;
        this.occurredAt = occurredAt;
    }

    ParcelStatusHistoryEntity(ParcelEntity parcel, StatusChangeData data) {
        this(parcel, data.fromStatus(), data.toStatus(), data.actorId(), data.actorRole(), data.note(), data.occurredAt());
        this.failureReason = data.failureReason();
        this.proofType = data.proofType();
        this.proofId = data.proofId();
        this.receivedBy = data.receivedBy();
        if (data.location() != null) {
            this.locationCity = data.location().city();
            this.locationDistrict = data.location().district();
            this.locationZoneCode = data.location().zoneCode();
        }
    }

    record StatusChangeData(ParcelStatus fromStatus, ParcelStatus toStatus, String actorId, ActorRole actorRole,
                            String note, Instant occurredAt, FailureReason failureReason, String proofType,
                            String proofId, String receivedBy, Location location) {
    }

    ParcelStatus getFromStatus() {
        return fromStatus;
    }

    ParcelStatus getToStatus() {
        return toStatus;
    }

    String getActorId() {
        return actorId;
    }

    ActorRole getActorRole() {
        return actorRole;
    }

    String getNote() {
        return note;
    }

    Instant getOccurredAt() {
        return occurredAt;
    }

    FailureReason getFailureReason() { return failureReason; }
    String getProofType() { return proofType; }
    String getProofId() { return proofId; }
    String getReceivedBy() { return receivedBy; }
    Location getLocation() {
        if (locationCity == null && locationDistrict == null && locationZoneCode == null) return null;
        return new Location(locationCity, locationDistrict, locationZoneCode);
    }
}
