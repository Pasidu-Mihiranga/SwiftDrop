import Link from "next/link";
import { AreaDashboard } from "@/components/area-dashboard";
import { MerchantNav } from "@/components/merchant-nav";

export const metadata = { title: "Merchant portal" };

export default function MerchantHome() {
  return (
    <div className="space-y-6">
      <MerchantNav current="/merchant" />
      <AreaDashboard
        title="Merchant portal"
        subtitle="Orders from your store, API credentials and webhooks."
        check={{ service: "merchant-service", path: "/api/v1/merchants/meta/whoami" }}
        upcoming={[{ feature: "Webhook endpoints & delivery log", owner: "M8" }]}
      />
      <div className="grid gap-4 md:grid-cols-2">
        <Link href="/merchant/orders" className="card block space-y-1 hover:border-brand-600">
          <h2 className="font-semibold">Orders</h2>
          <p className="text-sm text-slate-600">
            Every delivery your system sent through the API. Filter by status and date, and download them as CSV.
          </p>
        </Link>
        <Link href="/merchant/credentials" className="card block space-y-1 hover:border-brand-600">
          <h2 className="font-semibold">API credentials</h2>
          <p className="text-sm text-slate-600">
            Get the client ID and secret your system uses to send orders. The secret is shown once.
          </p>
        </Link>
      </div>
    </div>
  );
}
