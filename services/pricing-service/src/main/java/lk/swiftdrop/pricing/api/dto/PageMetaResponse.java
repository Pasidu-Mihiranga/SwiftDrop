package lk.swiftdrop.pricing.api.dto;

/** The shared pagination envelope from the common contract. */
public record PageMetaResponse(int page, int size, long totalElements, int totalPages) {
}
