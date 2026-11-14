package lk.swiftdrop.parcel.application.model;

import java.util.List;

/** One page of a list, without tying the application layer to Spring Data. */
public record PagedResult<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    public PagedResult {
        content = List.copyOf(content);
    }
}
