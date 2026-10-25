package lk.swiftdrop.pricing.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/** Maps the {@code zone} table created by {@code V2__create_zones.sql}. */
@Entity
@Table(name = "zone")
class ZoneEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "code", nullable = false, length = 64, updatable = false)
    private String code;

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Column(name = "active", nullable = false)
    private boolean active;

    @OneToMany(mappedBy = "zone")
    private Set<DistrictZoneEntity> districts = new LinkedHashSet<>();

    protected ZoneEntity() {
        // for JPA
    }

    ZoneEntity(UUID id, String code, String name, boolean active) {
        this.id = id;
        this.code = code;
        this.name = name;
        this.active = active;
    }

    /** The code is intentionally not mutable; only these two attributes can be edited. */
    void rename(String newName, boolean newActive) {
        this.name = newName;
        this.active = newActive;
    }

    UUID getId() {
        return id;
    }

    String getCode() {
        return code;
    }

    String getName() {
        return name;
    }

    boolean isActive() {
        return active;
    }

    Set<DistrictZoneEntity> getDistricts() {
        return districts;
    }
}
