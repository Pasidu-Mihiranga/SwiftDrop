import { AreaDashboard } from "@/components/area-dashboard";

export const metadata = { title: "Dispatch" };

export default function DispatchHome() {
  return (
    <AreaDashboard
      title="Dispatch board"
      subtitle="Unassigned parcels and courier workloads."
      check={{ service: "dispatch-service", path: "/api/v1/dispatch/meta/whoami" }}
      upcoming={[
        { feature: "Unassigned parcels by zone (live)", owner: "M5 · M6" },
        { feature: "Assign / reassign courier", owner: "M5" },
        { feature: "Courier workload view", owner: "M5" },
      ]}
    />
  );
}
