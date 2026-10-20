import { AreaDashboard } from "@/components/area-dashboard";

export const metadata = { title: "Merchant portal" };

export default function MerchantHome() {
  return (
    <AreaDashboard
      title="Merchant portal"
      subtitle="Orders from your store, API credentials and webhooks."
      check={{ service: "merchant-service", path: "/api/v1/merchants/meta/whoami" }}
      upcoming={[
        { feature: "Orders dashboard & CSV export", owner: "M7" },
        { feature: "API credentials (OAuth2 client credentials)", owner: "M7" },
        { feature: "Webhook endpoints & delivery log", owner: "M8" },
      ]}
    />
  );
}
