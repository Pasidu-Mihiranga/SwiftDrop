package lk.swiftdrop.parcel.api.dto;

import lk.swiftdrop.parcel.application.model.PagedResult;
import lk.swiftdrop.parcel.domain.model.Parcel;

import java.util.List;

/** The ParcelPage schema: a page of parcels with the shared PageMeta envelope. */
public record ParcelPageResponse(List<ParcelResponse> content, PageMeta page) {

    public record PageMeta(int page, int size, long totalElements, int totalPages) { }

    public static ParcelPageResponse from(PagedResult<Parcel> result) {
        return new ParcelPageResponse(result.content().stream().map(ParcelResponse::summary).toList(),
                new PageMeta(result.page(), result.size(), result.totalElements(), result.totalPages()));
    }
}
