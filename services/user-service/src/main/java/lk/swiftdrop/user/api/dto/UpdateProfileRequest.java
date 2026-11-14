package lk.swiftdrop.user.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Replaces the caller's name and phone number (PUT semantics: leaving {@code phone} out removes it).
 *
 * @param fullName 1 to 100 characters
 * @param phone    Sri Lankan mobile number such as {@code 0771234567} or {@code +94771234567}
 */
public record UpdateProfileRequest(
        @NotBlank @Size(max = 100) String fullName,

        @Pattern(regexp = "^(\\+94|0)7[0-9]{8}$",
                message = "must be a Sri Lankan mobile number such as 0771234567 or +94771234567")
        String phone) {
}
