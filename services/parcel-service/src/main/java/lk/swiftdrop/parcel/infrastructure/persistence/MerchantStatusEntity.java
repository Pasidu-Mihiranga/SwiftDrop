package lk.swiftdrop.parcel.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lk.swiftdrop.parcel.domain.model.MerchantStanding;
import lk.swiftdrop.parcel.domain.model.MerchantStatus;

import java.time.Instant;

/** Row of {@code merchant_status}: parcel-service's copy of one merchant's lifecycle status. */
@Entity
@Table(name = "merchant_status")
public class MerchantStatusEntity {

    @Id
    @Column(name = "merchant_id", length = 64)
    private String merchantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private MerchantStatus status;

    @Column(name = "business_name", nullable = false, length = 150)
    private String businessName;

    @Column(name = "contact_email", length = 254)
    private String contactEmail;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected MerchantStatusEntity() {
    }

    MerchantStanding toDomain() {
        return new MerchantStanding(merchantId, status, businessName, contactEmail, changedAt);
    }

    /** Takes over a newer standing; the caller has already decided that it is newer. */
    void replaceWith(MerchantStanding newer, Instant now) {
        this.status = newer.status();
        this.businessName = newer.businessName();
        this.contactEmail = newer.contactEmail();
        this.changedAt = newer.changedAt();
        this.updatedAt = now;
    }
}
