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

/** Where server-side code reaches the API gateway. */
export function gatewayUrl(env: Env = process.env): string {
  return (env.GATEWAY_URL ?? "http://localhost:8080").replace(/\/$/, "");
}
