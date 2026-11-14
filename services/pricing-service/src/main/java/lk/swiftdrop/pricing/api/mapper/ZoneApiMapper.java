package lk.swiftdrop.pricing.api.mapper;

import lk.swiftdrop.pricing.api.dto.DistrictAssignmentResponse;
import lk.swiftdrop.pricing.api.dto.ZoneResponse;
import lk.swiftdrop.pricing.domain.model.DistrictAssignment;
import lk.swiftdrop.pricing.domain.model.Zone;

/** Turns domain zones into the wire shapes published in the OpenAPI contract. */
public final class ZoneApiMapper {

    private ZoneApiMapper() {
    }

    /** Public coverage: the surrogate id is not exposed to anonymous callers. */
    public static ZoneResponse toPublicResponse(Zone zone) {
        return new ZoneResponse(null, zone.code().value(), zone.name(), zone.districts(), zone.active());
    }

    /** Admin view: the id is the handle used to update the zone. */
    public static ZoneResponse toAdminResponse(Zone zone) {
        return new ZoneResponse(zone.id(), zone.code().value(), zone.name(), zone.districts(), zone.active());
    }

    public static DistrictAssignmentResponse toResponse(DistrictAssignment assignment) {
        return new DistrictAssignmentResponse(assignment.district(), assignment.zoneCode().value(),
                assignment.zoneName());
    }
}
