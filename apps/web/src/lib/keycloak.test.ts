import { afterEach, describe, expect, it, vi } from "vitest";
import { appUrl, gatewayUrl, keycloakConfig, keycloakLogoutUrl, refreshKeycloakToken } from "./keycloak";

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

describe("refreshKeycloakToken", () => {
  const config = { internalUrl: "http://keycloak:8080/realms/swiftdrop", clientId: "swiftdrop-web", clientSecret: "s" };

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("exchanges the refresh token and returns a new access token with its own expiry", async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ access_token: "new-at", refresh_token: "new-rt", expires_in: 300 }), {
        status: 200,
      }),
    );
    vi.stubGlobal("fetch", fetchMock);

    const result = await refreshKeycloakToken(config, "old-rt");

    expect(result?.accessToken).toBe("new-at");
    expect(result?.refreshToken).toBe("new-rt");
    expect(result?.expiresAt).toBeGreaterThan(Date.now() / 1000 + 290);
    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe("http://keycloak:8080/realms/swiftdrop/protocol/openid-connect/token");
    expect(new URLSearchParams(init.body as string).get("refresh_token")).toBe("old-rt");
  });

  it("keeps the old refresh token when Keycloak does not rotate it", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(new Response(JSON.stringify({ access_token: "new-at", expires_in: 60 }))),
    );

    const result = await refreshKeycloakToken(config, "old-rt");

    expect(result?.refreshToken).toBe("old-rt");
  });

  it("returns undefined, not a throw, when the refresh is rejected", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(JSON.stringify({ error: "invalid_grant" }), { status: 400 })));

    expect(await refreshKeycloakToken(config, "expired-rt")).toBeUndefined();
  });

  it("returns undefined when the network call itself fails", async () => {
    vi.stubGlobal("fetch", vi.fn().mockRejectedValue(new Error("ECONNREFUSED")));

    expect(await refreshKeycloakToken(config, "old-rt")).toBeUndefined();
  });
});
