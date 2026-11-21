package lk.swiftdrop.user.api.dto;

import java.util.List;

/** One account in admin user management. */
public record AdminUserResponse(String id, String email, String fullName, boolean enabled, List<String> roles) {
}
