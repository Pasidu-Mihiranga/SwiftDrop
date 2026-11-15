import Link from "next/link";
import { AreaDashboard } from "@/components/area-dashboard";

export const metadata = { title: "My deliveries — SwiftDrop" };

export default function CustomerHome() {
  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-4 rounded-2xl border border-brand-100 bg-gradient-to-r from-brand-50 to-white p-5 shadow-xs">
        <div>
          <h2 className="text-base font-bold text-brand-900">Delivery History &amp; Receipts Ready</h2>
          <p className="text-sm text-slate-600">
            Browse all your past parcel bookings, filter by status or date range, and download official PDF delivery receipts.
          </p>
        </div>
        <Link href="/customer/history" className="btn-primary shrink-0 text-sm">
          Browse Delivery History &amp; Receipts &rarr;
        </Link>
      </div>

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
    </div>
  );
}
