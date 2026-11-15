package lk.swiftdrop.pricing.application.model;

import java.util.List;

/**
 * One page of results plus the totals the API publishes.
 *
 * <p>Deliberately framework-free so the outbound ports stay independent of Spring Data.
 */
public record Paged<T>(List<T> content, int page, int size, long totalElements) {

    public Paged {
        content = List.copyOf(content);
    }

    public int totalPages() {
        return size <= 0 ? 0 : (int) Math.ceilDiv(totalElements, size);
    }
}
