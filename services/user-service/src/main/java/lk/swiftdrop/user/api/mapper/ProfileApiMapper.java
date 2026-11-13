package lk.swiftdrop.user.api.mapper;

import lk.swiftdrop.common.security.AuthenticatedUser;
import lk.swiftdrop.common.web.error.BusinessRuleViolationException;
import lk.swiftdrop.user.api.dto.ProfileResponse;
import lk.swiftdrop.user.application.model.IdentityClaims;
import lk.swiftdrop.user.domain.model.PhoneNumber;
import lk.swiftdrop.user.domain.model.UserProfile;
import lk.swiftdrop.user.domain.model.UserRole;

public final class ProfileApiMapper {

    private ProfileApiMapper() {
    }

    /** Every Keycloak user signs in with an e-mail address, so a person token without one is refused. */
    public static IdentityClaims toClaims(AuthenticatedUser user) {
        String email = user.email().orElseThrow(() -> new BusinessRuleViolationException("EMAIL_REQUIRED",
                "The access token carries no e-mail address, so no profile can be created"));
        return new IdentityClaims(user.id(), email, user.name().orElse(null), UserRole.fromTokenRoles(user.roles()));
    }

    public static ProfileResponse toResponse(UserProfile profile) {
        return new ProfileResponse(profile.id(), profile.email(), profile.fullName(),
                profile.phoneNumber().map(PhoneNumber::value).orElse(null), profile.status().name(),
                profile.roles().stream().map(UserRole::name).sorted().toList(),
                profile.createdAt(), profile.updatedAt());
    }
}
