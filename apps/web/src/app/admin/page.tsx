import Link from "next/link";
import { AreaDashboard } from "@/components/area-dashboard";
import { auth } from "@/auth";

export const metadata = { title: "Operations" };

export default async function AdminHome() {
  const session = await auth();
  const isAdmin = session && !session.error ? session.user.roles.includes("ADMIN") : false;

  return (
    <div className="space-y-6">
      {isAdmin ? (
        <section className="card space-y-3">
          <h2 className="font-semibold">Zones &amp; pricing</h2>
          <p className="text-sm text-slate-600">
            Zone coverage, district assignments, size multipliers and the rate cards every quote is priced from.
          </p>
          <div className="flex flex-wrap gap-2">
            <Link className="btn-primary" href="/admin/pricing/zones">
              Zones &amp; districts
            </Link>
            <Link className="btn-secondary" href="/admin/pricing/rate-cards">
              Rate cards
            </Link>
          </div>
        </section>
      ) : null}

      <AreaDashboard
        title="Operations"
        subtitle="Dispatch, couriers, merchants, zones & pricing, reports."
        check={{ service: "pricing-service", path: "/api/v1/pricing/zones" }}
        upcoming={[
          { feature: "Dispatch board & courier workload", owner: "M5" },
          { feature: "Merchant approvals", owner: "M7" },
          { feature: "Users & couriers", owner: "M1" },
          { feature: "Reports (deliveries/day, success rate)", owner: "M10" },
          { feature: "Webhook dead-letter replay", owner: "M8" },
        ]}
      />
    </div>
  );
}
