import { AreaDashboard } from "@/components/area-dashboard";

export const metadata = { title: "Today's runs" };

export default function CourierHome() {
  return (
    <AreaDashboard
      title="Today's runs"
      subtitle="Your assigned pickups and deliveries for today."
      check={{ service: "dispatch-service", path: "/api/v1/dispatch/meta/whoami" }}
      upcoming={[
        { feature: "Run sheet for the day", owner: "M5" },
        { feature: "Status updates with proof of delivery (photo)", owner: "M4" },
        { feature: "Failed delivery with reason", owner: "M4" },
      ]}
    />
  );
}
