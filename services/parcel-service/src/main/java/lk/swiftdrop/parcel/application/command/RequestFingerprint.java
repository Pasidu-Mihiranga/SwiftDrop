package lk.swiftdrop.parcel.application.command;

import lk.swiftdrop.parcel.domain.model.Address;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * SHA-256 of a merchant order's canonical form: the Idempotency-Key "same request" test.
 *
 * <p>The hash is taken over the validated command, not the raw JSON, so field order, whitespace
 * and number spelling ({@code 2}, {@code 2.0}, {@code 2.00}) do not change it. Every field is
 * written with its name and length ({@code name=5:value;}), so no two different orders can produce
 * the same text, and a missing value is distinct from an empty one.
 */
public final class RequestFingerprint {

    private RequestFingerprint() {
    }

    /** @return 64 lower-case hex characters */
    public static String of(MerchantOrderCommand order) {
        StringBuilder canonical = new StringBuilder(512);
        field(canonical, "v", "1");
        field(canonical, "merchantOrderRef", order.merchantOrderRef());
        address(canonical, "pickup", order.pickup());
        address(canonical, "dropoff", order.dropoff());
        field(canonical, "recipient.name", order.recipient().name());
        field(canonical, "recipient.phone", order.recipient().phone());
        field(canonical, "recipient.email", order.recipient().email());
        field(canonical, "weightKg", number(order.weightKg()));
        field(canonical, "sizeClass", order.sizeClass().name());
        field(canonical, "serviceLevel", order.serviceLevel().name());
        field(canonical, "codAmount", number(order.codAmount()));
        return sha256(canonical.toString());
    }

    private static void address(StringBuilder out, String prefix, Address address) {
        field(out, prefix + ".line1", address.line1());
        field(out, prefix + ".line2", address.line2());
        field(out, prefix + ".city", address.city());
        field(out, prefix + ".district", address.district());
        field(out, prefix + ".postalCode", address.postalCode());
        field(out, prefix + ".lat", number(address.lat()));
        field(out, prefix + ".lng", number(address.lng()));
    }

    private static void field(StringBuilder out, String name, String value) {
        out.append(name).append('=');
        if (value == null) {
            out.append('-');
        } else {
            out.append(value.length()).append(':').append(value);
        }
        out.append(';');
    }

    /** One spelling per value: 2, 2.0 and 2.00 are the same weight. */
    private static String number(BigDecimal value) {
        return value == null ? null : value.stripTrailingZeros().toPlainString();
    }

    private static String sha256(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("Every Java runtime provides SHA-256", impossible);
        }
    }
}
