package lk.swiftdrop.user.api.dto;

import java.util.List;

/** One page of a user search; {@code page} is zero-based. */
public record AdminUserPageResponse(List<AdminUserResponse> items, long total, int page, int size) {
}
