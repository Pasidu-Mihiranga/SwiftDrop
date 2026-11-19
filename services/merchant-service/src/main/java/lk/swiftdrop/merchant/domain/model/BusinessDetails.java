package lk.swiftdrop.merchant.domain.model;

import java.util.Locale;
import java.util.Objects;

/**
 * What a business states about itself when it applies. The business registration number is
 * normalised here, once, so that "pv 1234" and "PV1234" are recognised as the same business.
 *
 * @param website optional
 */
public record BusinessDetails(String businessName, String brNumber, String contactEmail, String contactPhone,
                              String website, PickupAddress pickupAddress) {

    public BusinessDetails {
        Objects.requireNonNull(businessName, "businessName");
        Objects.requireNonNull(brNumber, "brNumber");
        Objects.requireNonNull(contactEmail, "contactEmail");
        Objects.requireNonNull(contactPhone, "contactPhone");
        Objects.requireNonNull(pickupAddress, "pickupAddress");
        businessName = businessName.strip();
        brNumber = normaliseBrNumber(brNumber);
        contactEmail = contactEmail.strip();
        contactPhone = contactPhone.strip();
        website = website == null || website.isBlank() ? null : website.strip();
    }

    /** Upper case with all whitespace removed. */
    public static String normaliseBrNumber(String brNumber) {
        return brNumber.replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
    }

    /** The phone number and address are personal data and stay out of logs. */
    @Override
    public String toString() {
        return "BusinessDetails[businessName=" + businessName + ", brNumber=" + brNumber + ", personal data masked]";
    }
}
