package lk.swiftdrop.parcel.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lk.swiftdrop.parcel.domain.model.Recipient;

/** The Contact schema in contracts/openapi/parcel-service.v1.yaml: a Sri Lankan mobile number is required. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ContactDto(
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Pattern(regexp = "^(\\+94|0)7[0-9]{8}$", message = "must be a Sri Lankan mobile number") String phone,
        @Email @Size(max = 254) String email) {

    public Recipient toDomain() {
        return new Recipient(name.strip(), phone, email == null || email.isBlank() ? null : email.strip());
    }

    public static ContactDto from(Recipient recipient) {
        return new ContactDto(recipient.name(), recipient.phone(), recipient.email());
    }
}
