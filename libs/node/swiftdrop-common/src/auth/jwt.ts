import { createRemoteJWKSet, jwtVerify, type JWTPayload, type JWTVerifyGetKey } from "jose";

export const Roles = {
  CUSTOMER: "CUSTOMER",
  MERCHANT: "MERCHANT",
  MERCHANT_API: "MERCHANT_API",
  COURIER: "COURIER",
  DISPATCHER: "DISPATCHER",
  ADMIN: "ADMIN",
} as const;
export type Role = (typeof Roles)[keyof typeof Roles];

/** The caller, built from a Keycloak access token (same shape as Java's AuthenticatedUser). */
export interface AuthenticatedUser {
  id: string;
  email?: string;
  name?: string;
  roles: Role[];
  merchantId?: string;
}

export interface JwtVerifierOptions {
  /** Browser-facing issuer, e.g. http://localhost:8180/realms/swiftdrop */
  issuer: string;
  /** Expected audience (Keycloak audience mapper adds "swiftdrop-api"). */
  audience: string;
  /** Key source: use remoteJwks(url) in services, a local key set in tests. */
  keys: JWTVerifyGetKey;
}

export class InvalidTokenError extends Error {
  constructor(message = "Invalid or expired access token") {
    super(message);
    this.name = "InvalidTokenError";
  }
}

/** JWKS fetched (and cached) from Keycloak over the internal network. */
export function remoteJwks(jwksUri: string): JWTVerifyGetKey {
  return createRemoteJWKSet(new URL(jwksUri), { cooldownDuration: 30_000, cacheMaxAge: 10 * 60_000 });
}

const KNOWN_ROLES = new Set<string>(Object.values(Roles));

export function toAuthenticatedUser(payload: JWTPayload): AuthenticatedUser {
  const realmAccess = payload.realm_access as { roles?: unknown } | undefined;
  const roles = Array.isArray(realmAccess?.roles) ? realmAccess.roles.filter((r): r is Role => KNOWN_ROLES.has(String(r))) : [];
  return {
    id: String(payload.sub),
    email: typeof payload.email === "string" ? payload.email : undefined,
    name: typeof payload.name === "string" ? payload.name : undefined,
    roles,
    merchantId: typeof payload.merchant_id === "string" ? payload.merchant_id : undefined,
  };
}

/** Returns a function that verifies signature, issuer, audience and expiry, then maps the claims. */
export function createJwtVerifier(options: JwtVerifierOptions) {
  return async function verify(token: string): Promise<AuthenticatedUser> {
    try {
      const { payload } = await jwtVerify(token, options.keys, { issuer: options.issuer, audience: options.audience });
      if (!payload.sub) throw new InvalidTokenError("Token has no subject");
      return toAuthenticatedUser(payload);
    } catch (error) {
      if (error instanceof InvalidTokenError) throw error;
      throw new InvalidTokenError();
    }
  };
}

export type JwtVerifier = ReturnType<typeof createJwtVerifier>;

/** Extracts the token from an "Authorization: Bearer ..." header value. */
export function bearerToken(header: string | string[] | undefined): string | undefined {
  const value = Array.isArray(header) ? header[0] : header;
  const match = value?.match(/^Bearer\s+(.+)$/i);
  return match?.[1];
}
