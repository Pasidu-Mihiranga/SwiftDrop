import { describe, expect, it } from "vitest";
import { gatewayUrl, keycloakConfig } from "./keycloak";

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
