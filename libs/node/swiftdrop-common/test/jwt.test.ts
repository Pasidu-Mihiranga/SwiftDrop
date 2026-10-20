import { bearerToken, createJwtVerifier, InvalidTokenError, remoteJwks, toAuthenticatedUser } from "../src";
import { AUDIENCE, ISSUER, testKeys } from "./support/tokens";

describe("JWT verification", () => {
  it("maps a valid Keycloak token to an AuthenticatedUser", async () => {
    const { keys, sign } = await testKeys();
    const verify = createJwtVerifier({ issuer: ISSUER, audience: AUDIENCE, keys });
    const token = await sign({
      sub: "user-1",
      email: "merchant@swiftdrop.lk",
      name: "Malith Merchant",
      merchant_id: "m-1",
      realm_access: { roles: ["MERCHANT", "offline_access", "default-roles-swiftdrop"] },
    });

    await expect(verify(token)).resolves.toEqual({
      id: "user-1",
      email: "merchant@swiftdrop.lk",
      name: "Malith Merchant",
      roles: ["MERCHANT"],
      merchantId: "m-1",
    });
  });

  it.each([
    ["wrong audience", { audience: "other-api" }],
    ["wrong issuer", { issuer: "http://evil/realms/swiftdrop" }],
    ["expired", { expiresIn: "-1m" }],
  ])("rejects tokens with %s", async (_name, options) => {
    const { keys, sign } = await testKeys();
    const verify = createJwtVerifier({ issuer: ISSUER, audience: AUDIENCE, keys });
    await expect(verify(await sign({ sub: "u" }, options))).rejects.toBeInstanceOf(InvalidTokenError);
  });

  it("rejects tokens signed by another key and tokens without subject", async () => {
    const trusted = await testKeys();
    const attacker = await testKeys();
    const verify = createJwtVerifier({ issuer: ISSUER, audience: AUDIENCE, keys: trusted.keys });

    await expect(verify(await attacker.sign({ sub: "u" }))).rejects.toBeInstanceOf(InvalidTokenError);
    await expect(verify(await trusted.sign({}))).rejects.toThrow("Token has no subject");
  });

  it("parses bearer headers", () => {
    expect(bearerToken("Bearer abc.def")).toBe("abc.def");
    expect(bearerToken(["bearer xyz"])).toBe("xyz");
    expect(bearerToken("Basic abc")).toBeUndefined();
    expect(bearerToken(undefined)).toBeUndefined();
  });

  it("handles tokens without realm roles", () => {
    expect(toAuthenticatedUser({ sub: "svc" }).roles).toEqual([]);
  });

  it("creates a remote key set for Keycloak", () => {
    expect(typeof remoteJwks("http://keycloak:8080/realms/swiftdrop/protocol/openid-connect/certs")).toBe("function");
  });
});
