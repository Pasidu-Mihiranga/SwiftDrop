package lk.swiftdrop.parcel.domain.model;

/** Who receives the parcel. The phone number and email are personal data, encrypted at rest. */
public record Recipient(String name, String phone, String email) {
}
