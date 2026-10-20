import { createLocalJWKSet, exportJWK, generateKeyPair, type JWTVerifyGetKey, SignJWT } from "jose";

export const ISSUER = "http://localhost:8180/realms/swiftdrop";
export const AUDIENCE = "swiftdrop-api";

export interface TestKeys {
  keys: JWTVerifyGetKey;
  sign(claims: Record<string, unknown>, options?: { audience?: string; expiresIn?: string; issuer?: string }): Promise<string>;
}

/** Local RSA key pair standing in for Keycloak in tests. */
export async function testKeys(): Promise<TestKeys> {
  const { privateKey, publicKey } = await generateKeyPair("RS256");
  const jwk = { ...(await exportJWK(publicKey)), kid: "test", alg: "RS256" };
  return {
    keys: createLocalJWKSet({ keys: [jwk] }),
    sign: (claims, options = {}) =>
      new SignJWT(claims)
        .setProtectedHeader({ alg: "RS256", kid: "test" })
        .setIssuer(options.issuer ?? ISSUER)
        .setAudience(options.audience ?? AUDIENCE)
        .setIssuedAt()
        .setExpirationTime(options.expiresIn ?? "5m")
        .sign(privateKey),
  };
}
