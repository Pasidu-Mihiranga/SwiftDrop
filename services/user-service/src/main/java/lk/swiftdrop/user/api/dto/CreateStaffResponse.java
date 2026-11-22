package lk.swiftdrop.user.api.dto;

/**
 * The new account.
 *
 * @param invitationSent whether Keycloak accepted the "set your password" e-mail; if not, send it again
 */
public record CreateStaffResponse(AdminUserResponse user, boolean invitationSent) {
}
