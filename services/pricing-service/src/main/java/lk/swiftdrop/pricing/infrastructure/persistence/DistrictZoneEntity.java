package lk.swiftdrop.pricing.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Maps the {@code district_zone} table. The district name is the primary key. */
@Entity
@Table(name = "district_zone")
class DistrictZoneEntity {

    @Id
    @Column(name = "district", nullable = false, length = 100, updatable = false)
    private String district;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "zone_id", nullable = false)
    private ZoneEntity zone;

    protected DistrictZoneEntity() {
        // for JPA
    }

    DistrictZoneEntity(String district, ZoneEntity zone) {
        this.district = district;
        this.zone = zone;
    }

    void moveTo(ZoneEntity newZone) {
        this.zone = newZone;
    }

    String getDistrict() {
        return district;
    }

    ZoneEntity getZone() {
        return zone;
    }
}
