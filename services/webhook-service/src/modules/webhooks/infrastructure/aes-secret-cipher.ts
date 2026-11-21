import { createCipheriv, createDecipheriv, randomBytes } from "node:crypto";
import type { SecretCipher } from "../application/ports";

const VERSION_PREFIX = "v1:";
const IV_BYTES = 12;
const TAG_BYTES = 16;
const KEY_BYTES = 32;

/**
 * Encrypts signing secrets with AES-256-GCM. Secrets are encrypted rather than hashed because the
 * worker needs the plain value to sign. Stored form: "v1:" + base64(IV || ciphertext || tag), the
 * same format the Java services use for personal data.
 */
export class AesSecretCipher implements SecretCipher {
  private readonly key: Buffer;

  /** @param base64Key 32 random bytes, base64 encoded; the service refuses to start without one */
  constructor(base64Key: string | undefined) {
    const key = Buffer.from((base64Key ?? "").trim(), "base64");
    if (key.length !== KEY_BYTES) {
      throw new Error("WEBHOOK_SECRET_KEY must be a base64-encoded 32-byte key (run pnpm env:init)");
    }
    this.key = key;
  }

  encrypt(plainText: string): string {
    const iv = randomBytes(IV_BYTES);
    const cipher = createCipheriv("aes-256-gcm", this.key, iv);
    const sealed = Buffer.concat([cipher.update(plainText, "utf8"), cipher.final(), cipher.getAuthTag()]);
    return VERSION_PREFIX + Buffer.concat([iv, sealed]).toString("base64");
  }

  decrypt(stored: string): string {
    if (!stored.startsWith(VERSION_PREFIX)) throw new Error("Encrypted secret has an unknown format");
    const data = Buffer.from(stored.slice(VERSION_PREFIX.length), "base64");
    if (data.length <= IV_BYTES + TAG_BYTES) throw new Error("Encrypted secret is truncated");
    try {
      const decipher = createDecipheriv("aes-256-gcm", this.key, data.subarray(0, IV_BYTES));
      decipher.setAuthTag(data.subarray(data.length - TAG_BYTES));
      return Buffer.concat([decipher.update(data.subarray(IV_BYTES, data.length - TAG_BYTES)), decipher.final()]).toString("utf8");
    } catch {
      // Wrong key or a tampered value. The value itself never appears in the message.
      throw new Error("Could not decrypt a webhook secret");
    }
  }
}
