type Env = Record<string, string | undefined>;

/** Keycloak settings from the environment (root .env locally, container env in Docker/Kubernetes). */
export interface KeycloakConfig {
  /** Browser-facing realm URL; also the token issuer. */
  issuer: string;
  /** Realm URL reachable from the Next.js server (differs from issuer inside Docker). */
  internalUrl: string;
  clientId: string;
  clientSecret: string;
}

export function keycloakConfig(env: Env = process.env): KeycloakConfig {
  const realm = env.KEYCLOAK_REALM ?? "swiftdrop";
  const issuer = env.AUTH_KEYCLOAK_ISSUER ?? `${env.KEYCLOAK_PUBLIC_URL ?? "http://localhost:8180"}/realms/${realm}`;
  return {
    issuer,
    internalUrl: env.AUTH_KEYCLOAK_INTERNAL_URL ?? issuer,
    clientId: env.AUTH_KEYCLOAK_ID ?? "swiftdrop-web",
    clientSecret: env.AUTH_KEYCLOAK_SECRET ?? env.KC_WEB_CLIENT_SECRET ?? "",
  };
}

/**
 * Keycloak's RP-initiated logout URL (browser-facing). Clearing the Auth.js cookie alone leaves the
 * Keycloak SSO session alive, so the next "Sign in" would silently log the previous user back in.
 * With the ID token as a hint Keycloak ends that session without asking; without one (expired
 * session) it shows its own "Do you want to log out?" page.
 */
export function keycloakLogoutUrl(
  config: Pick<KeycloakConfig, "issuer" | "clientId">,
  postLogoutRedirectUri: string,
  idToken?: string,
): string {
  const url = new URL(`${config.issuer}/protocol/openid-connect/logout`);
  url.searchParams.set("client_id", config.clientId);
  url.searchParams.set("post_logout_redirect_uri", postLogoutRedirectUri);
  if (idToken) url.searchParams.set("id_token_hint", idToken);
  return url.toString();
}

/** Public URL of this web app (Auth.js AUTH_URL), used for redirects back from Keycloak. */
export function appUrl(env: Env = process.env): string {
  return (env.AUTH_URL ?? "http://localhost:3000").replace(/\/$/, "");
}

/** Where server-side code reaches the API gateway. */
export function gatewayUrl(env: Env = process.env): string {
  return (env.GATEWAY_URL ?? "http://localhost:8080").replace(/\/$/, "");
}

/** A fresh set of Keycloak tokens, as returned from the token endpoint. */
export interface RefreshedTokens {
  accessToken: string;
  refreshToken: string;
  idToken?: string;
  /** Unix seconds. */
  expiresAt: number;
}

/**
 * Exchanges a refresh token for a new access token.
 *
 * Shared by the Auth.js callback (which persists the result into the session cookie) and
 * {@link gatewayFetch} (which cannot write the cookie from inside a Server Component, so it
 * uses the fresh token for just the one outgoing call instead). Returns undefined rather than
 * throwing: Keycloak may have already ended the session (idle timeout, sign-out elsewhere), and
 * that is an ordinary "sign in again" case, not a bug.
 */
export async function refreshKeycloakToken(
  config: Pick<KeycloakConfig, "internalUrl" | "clientId" | "clientSecret">,
  refreshToken: string,
): Promise<RefreshedTokens | undefined> {
  try {
    const response = await fetch(`${config.internalUrl}/protocol/openid-connect/token`, {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
      body: new URLSearchParams({
        grant_type: "refresh_token",
        refresh_token: refreshToken,
        client_id: config.clientId,
        client_secret: config.clientSecret,
      }),
      cache: "no-store",
    });
    const body = (await response.json()) as {
      access_token?: string;
      refresh_token?: string;
      id_token?: string;
      expires_in?: number;
    };
    if (!response.ok || !body.access_token) return undefined;
    return {
      accessToken: body.access_token,
      refreshToken: body.refresh_token ?? refreshToken,
      idToken: body.id_token,
      expiresAt: Math.floor(Date.now() / 1000) + (body.expires_in ?? 300),
    };
  } catch {
    return undefined;
  }
}
