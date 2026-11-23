/** Settings from the environment (12-factor). Every value has a local default except the secrets. */
export function loadConfig(env = process.env) {
  const port = Number(env.SIMULATOR_PORT ?? env.PORT ?? 4000);
  const keycloak = (env.KEYCLOAK_URL ?? "http://localhost:8180").replace(/\/+$/, "");
  const realm = env.KEYCLOAK_REALM ?? "swiftdrop";
  return {
    port,
    gatewayUrl: (env.GATEWAY_URL ?? "http://localhost:8080").replace(/\/+$/, ""),
    tokenUrl: env.TOKEN_URL ?? `${keycloak}/realms/${realm}/protocol/openid-connect/token`,
    clientId: env.MERCHANT_CLIENT_ID ?? "merchant-demo",
    clientSecret: env.MERCHANT_CLIENT_SECRET ?? env.KC_DEMO_MERCHANT_CLIENT_SECRET ?? "",
    // The endpoint's signing secret, shown once when the endpoint is registered in the merchant portal.
    webhookSecret: env.WEBHOOK_SECRET ?? "",
    // Where SwiftDrop should send webhooks, shown on the page to copy into the portal.
    publicWebhookUrl: env.PUBLIC_WEBHOOK_URL ?? `http://merchant-simulator:${port}/webhooks`,
    toleranceSeconds: Number(env.SIGNATURE_TOLERANCE_SECONDS ?? 300),
  };
}
