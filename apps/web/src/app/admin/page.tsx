import { AreaDashboard } from "@/components/area-dashboard";

export const metadata = { title: "Operations" };

export default function AdminHome() {
  return (
    <AreaDashboard
      title="Operations"
      subtitle="Dispatch, couriers, merchants, zones & pricing, reports."
      check={{ service: "user-service", path: "/api/v1/users/meta/whoami" }}
      upcoming={[
        { feature: "Dispatch board & courier workload", owner: "M5" },
        { feature: "Merchant approvals", owner: "M7" },
        { feature: "Zones & pricing", owner: "M2" },
        { feature: "Users & couriers", owner: "M1" },
        { feature: "Reports (deliveries/day, success rate)", owner: "M10" },
        { feature: "Webhook dead-letter replay", owner: "M8" },
      ]}
    />
  );
}
