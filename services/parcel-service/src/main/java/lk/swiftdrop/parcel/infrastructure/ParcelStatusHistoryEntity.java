package lk.swiftdrop.parcel.infrastructure;

import jakarta.persistence.*;
import lk.swiftdrop.events.common.Types;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "parcel_status_history")
public class ParcelStatusHistoryEntity {
    @Id private UUID id;
    @Column(name = "parcel_id", nullable = false) private UUID parcelId;
    @Enumerated(EnumType.STRING) @Column(name = "from_status") private Types.ParcelStatus fromStatus;
    @Enumerated(EnumType.STRING) @Column(name = "to_status", nullable = false) private Types.ParcelStatus toStatus;
    private String note;
    @Enumerated(EnumType.STRING) @Column(name = "failure_reason") private Types.FailureReason failureReason;
    @Enumerated(EnumType.STRING) @Column(name = "proof_type") private lk.swiftdrop.events.parcel.ParcelStatusChanged.ProofType proofType;
    @Column(name = "proof_id") private String proofId;
    @Column(name = "received_by") private String receivedBy;
    @Column(name = "location_city") private String locationCity;
    @Column(name = "location_district") private String locationDistrict;
    @Column(name = "location_zone_code") private String locationZoneCode;
    @Column(name = "changed_by", nullable = false) private String changedBy;
    @Column(name = "changed_by_role", nullable = false) private String changedByRole;
    @Column(name = "changed_at", nullable = false) private Instant changedAt;
    protected ParcelStatusHistoryEntity() { }
    public ParcelStatusHistoryEntity(UUID parcelId, Types.ParcelStatus from, Types.ParcelStatus to, String note,
            Types.FailureReason reason, lk.swiftdrop.events.parcel.ParcelStatusChanged.Proof proof,
            Types.Location location, String changedBy, String role, Instant changedAt) {
        this.id=UUID.randomUUID(); this.parcelId=parcelId; this.fromStatus=from; this.toStatus=to; this.note=note;
        this.failureReason=reason; this.proofType=proof == null ? null : proof.type(); this.proofId=proof == null ? null : proof.proofId();
        this.receivedBy=proof == null ? null : proof.receivedBy(); this.locationCity=location == null ? null : location.city();
        this.locationDistrict=location == null ? null : location.district(); this.locationZoneCode=location == null ? null : location.zoneCode();
        this.changedBy=changedBy; this.changedByRole=role; this.changedAt=changedAt;
    }
}
