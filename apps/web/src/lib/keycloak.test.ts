import { describe, expect, it } from "vitest";
import { appUrl, gatewayUrl, keycloakConfig, keycloakLogoutUrl } from "./keycloak";

describe("keycloakLogoutUrl", () => {
  const config = { issuer: "http://localhost:8180/realms/swiftdrop", clientId: "swiftdrop-web" };

  it("ends the Keycloak session for the signed-in user and comes back to the app", () => {
    const url = new URL(keycloakLogoutUrl(config, "http://localhost:3000/", "id.token.value"));
    expect(url.origin + url.pathname).toBe("http://localhost:8180/realms/swiftdrop/protocol/openid-connect/logout");
    expect(url.searchParams.get("client_id")).toBe("swiftdrop-web");
    expect(url.searchParams.get("post_logout_redirect_uri")).toBe("http://localhost:3000/");
    expect(url.searchParams.get("id_token_hint")).toBe("id.token.value");
  });

  it("still logs out when the session no longer has an ID token", () => {
    const url = new URL(keycloakLogoutUrl(config, "http://localhost:3000/"));
    expect(url.searchParams.has("id_token_hint")).toBe(false);
    expect(url.searchParams.get("client_id")).toBe("swiftdrop-web");
  });

  it("uses the public app URL for the way back", () => {
    expect(appUrl({ AUTH_URL: "https://swiftdrop.local/" })).toBe("https://swiftdrop.local");
    expect(appUrl({})).toBe("http://localhost:3000");
  });
});

describe("keycloakConfig", () => {
  it("derives everything from the shared .env variables", () => {
    expect(keycloakConfig({ KEYCLOAK_PUBLIC_URL: "http://localhost:8180", KC_WEB_CLIENT_SECRET: "s" })).toEqual({
      issuer: "http://localhost:8180/realms/swiftdrop",
      internalUrl: "http://localhost:8180/realms/swiftdrop",
      clientId: "swiftdrop-web",
      clientSecret: "s",
    });
  });

  it("uses a separate internal URL inside Docker", () => {
    const cfg = keycloakConfig({
      AUTH_KEYCLOAK_ISSUER: "http://localhost:8180/realms/swiftdrop",
      AUTH_KEYCLOAK_INTERNAL_URL: "http://keycloak:8080/realms/swiftdrop",
      AUTH_KEYCLOAK_SECRET: "x",
    });
    expect(cfg.internalUrl).toBe("http://keycloak:8080/realms/swiftdrop");
    expect(cfg.issuer).toBe("http://localhost:8180/realms/swiftdrop");
  });

  it("normalises the gateway URL", () => {
    expect(gatewayUrl({ GATEWAY_URL: "http://api-gateway:8080/" })).toBe("http://api-gateway:8080");
    expect(gatewayUrl({})).toBe("http://localhost:8080");
  });
});
