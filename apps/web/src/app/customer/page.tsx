import { AreaDashboard } from "@/components/area-dashboard";

export const metadata = { title: "My deliveries" };

export default function CustomerHome() {
  return (
    <AreaDashboard
      title="My deliveries"
      subtitle="Send parcels, follow them live and download receipts."
      check={{ service: "parcel-service", path: "/api/v1/parcels/meta/whoami" }}
      upcoming={[
        { feature: "Create a delivery with instant fee estimate", owner: "M3 · M2" },
        { feature: "Live tracking timeline", owner: "M6" },
        { feature: "Delivery history & receipts", owner: "M9" },
        { feature: "Profile & address book", owner: "M1" },
      ]}
    />
  );
}
