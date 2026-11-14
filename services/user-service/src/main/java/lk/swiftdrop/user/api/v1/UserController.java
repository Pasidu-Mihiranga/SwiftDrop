package lk.swiftdrop.user.api.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lk.swiftdrop.common.security.AuthenticatedUser;
import lk.swiftdrop.user.api.dto.ProfileResponse;
import lk.swiftdrop.user.api.dto.UpdateProfileRequest;
import lk.swiftdrop.user.api.mapper.ProfileApiMapper;
import lk.swiftdrop.user.application.command.UserProfileService;
import lk.swiftdrop.user.domain.model.PhoneNumber;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The signed-in person's own profile. Only person roles may call it; a merchant's machine client
 * ({@code MERCHANT_API}) has no profile.
 */
@RestController
@RequestMapping("/api/v1/users/me")
@PreAuthorize("hasAnyRole('CUSTOMER', 'MERCHANT', 'COURIER', 'DISPATCHER', 'ADMIN')")
@Tag(name = "Profile", description = "The caller's own profile")
public class UserController {

    private final UserProfileService profiles;

    public UserController(UserProfileService profiles) {
        this.profiles = profiles;
    }

    @GetMapping
    @Operation(summary = "The caller's profile, created from the token on the first call")
    public ProfileResponse me(AuthenticatedUser user) {
        return ProfileApiMapper.toResponse(profiles.getOrCreate(ProfileApiMapper.toClaims(user)));
    }

    @PutMapping
    @Operation(summary = "Replace the caller's name and phone number (omit phone to remove it)")
    public ProfileResponse update(AuthenticatedUser user, @Valid @RequestBody UpdateProfileRequest request) {
        PhoneNumber phone = request.phone() == null ? null : PhoneNumber.parse(request.phone());
        return ProfileApiMapper.toResponse(
                profiles.updateContactDetails(ProfileApiMapper.toClaims(user), request.fullName(), phone));
    }
}
