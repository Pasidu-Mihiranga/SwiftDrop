package lk.swiftdrop.pricing.application.query;

import lk.swiftdrop.pricing.domain.model.SizeClassRate;
import lk.swiftdrop.pricing.domain.policy.PricingContext;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * A short fingerprint of the configuration a quote was priced with.
 *
 * <p>Two quotes carry the same version exactly when every rate, band, setting and option that
 * priced them was identical, which is what support needs when a customer asks why yesterday's
 * price differs from today's. The measured distance is left out on purpose: it is a property of
 * the route, not of the tariff.
 *
 * <p>Built from the records' own string form. Every component is a record of values, so the
 * text is deterministic for a given configuration; it is hashed so the result is short and
 * reveals nothing about the rates.
 */
final class PricingVersion {

    private static final int LENGTH = 12;

    private PricingVersion() {
    }

    static String of(PricingContext.Rates rates, SizeClassRate sizeClass) {
        PricingContext.Rates tariff = new PricingContext.Rates(rates.card(), rates.weightTiers(),
                rates.originRemoteArea(), rates.destinationRemoteArea(), rates.insuranceBands(),
                rates.merchantAgreement(), rates.distanceRate(), null, rates.distanceBand(),
                rates.settings(), rates.specialHandling(), rates.vehicle());
        return hash(tariff + "|" + sizeClass).substring(0, LENGTH);
    }

    private static String hash(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by the Java platform", e);
        }
    }
}
