import { AreaDashboard } from "@/components/area-dashboard";

export const metadata = { title: "Operations" };

export default function AdminHome() {
  return (
    <AreaDashboard
      title="Operations"
      subtitle="Dispatch, couriers, merchants, zones & pricing, reports."
      check={{ service: "user-service", path: "/api/v1/users/meta/whoami" }}
      available={[
        {
          feature: "Delivery reports",
          description: "Deliveries per day, success rate, and outcomes by zone and courier.",
          href: "/admin/reports",
        },
      ]}
      upcoming={[
        { feature: "Dispatch board & courier workload", owner: "M5" },
        { feature: "Merchant approvals", owner: "M7" },
        { feature: "Zones & pricing", owner: "M2" },
        { feature: "Users & couriers", owner: "M1" },
        { feature: "Webhook dead-letter replay", owner: "M8" },
      ]}
    />
  );
}
