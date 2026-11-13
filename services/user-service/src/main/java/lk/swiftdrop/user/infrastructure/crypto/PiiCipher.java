package lk.swiftdrop.user.infrastructure.crypto;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Encrypts personal data (phone numbers, address lines) before it reaches the database, using
 * AES-256-GCM with a fresh random 96-bit IV per value.
 *
 * <p>Stored form: {@code v1:} + base64(IV || ciphertext || 128-bit tag). The version prefix leaves
 * room for key rotation later. GCM authenticates the data, so a value altered in the database fails
 * to decrypt instead of returning garbage.
 */
public final class PiiCipher {

    private static final String VERSION_PREFIX = "v1:";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final int KEY_BYTES = 32;

    private final SecretKey key;
    private final SecureRandom random = new SecureRandom();

    /**
     * @param base64Key 32 random bytes, base64 encoded ({@code PII_ENCRYPTION_KEY}; {@code pnpm env:init}
     *                  generates one). The service refuses to start without a valid key.
     */
    public PiiCipher(String base64Key) {
        byte[] raw;
        try {
            raw = Base64.getDecoder().decode(base64Key == null ? "" : base64Key.strip());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("PII_ENCRYPTION_KEY is not valid base64");
        }
        if (raw.length != KEY_BYTES) {
            throw new IllegalStateException("PII_ENCRYPTION_KEY must be a base64-encoded 32-byte key (run pnpm env:init)");
        }
        this.key = new SecretKeySpec(raw, "AES");
    }

    public String encrypt(String plainText) {
        byte[] iv = new byte[IV_BYTES];
        random.nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] sealed = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            byte[] stored = ByteBuffer.allocate(iv.length + sealed.length).put(iv).put(sealed).array();
            return VERSION_PREFIX + Base64.getEncoder().encodeToString(stored);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Could not encrypt a personal-data value", e);
        }
    }

    public String decrypt(String stored) {
        if (stored == null || !stored.startsWith(VERSION_PREFIX)) {
            throw new IllegalStateException("Encrypted value has an unknown format");
        }
        try {
            byte[] data = Base64.getDecoder().decode(stored.substring(VERSION_PREFIX.length()));
            if (data.length <= IV_BYTES) {
                throw new IllegalStateException("Encrypted value is truncated");
            }
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, data, 0, IV_BYTES));
            return new String(cipher.doFinal(data, IV_BYTES, data.length - IV_BYTES), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            // Wrong key or tampered value. The value itself is never included in the message.
            throw new IllegalStateException("Could not decrypt a personal-data value", e);
        }
    }
}
