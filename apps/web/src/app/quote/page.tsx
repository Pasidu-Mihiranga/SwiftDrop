import Link from "next/link";
import { FeeEstimateWidget } from "@/components/fee-estimate-widget";
import { mapConfig } from "@/lib/map-config";
import { fetchActiveZones, fetchSpecialHandling } from "@/lib/pricing-api";

export const metadata = {
  title: "Calculate Delivery Price - SwiftDrop",
  description: "Get an instant delivery cost estimate across Sri Lanka. No account needed.",
};

/** Coverage is read on the server so no zone list or API host ever ships to the browser. */
export default async function QuotePage() {
  const [{ zones, error }, { options: specialHandling }] = await Promise.all([
    fetchActiveZones(),
    fetchSpecialHandling(),
  ]);

  return (
    <div className="space-y-6">
      <nav aria-label="Breadcrumb" className="text-xs text-slate-500">
        <Link href="/" className="hover:text-slate-800">
          SwiftDrop
        </Link>
        <span className="mx-1.5 text-slate-300">/</span>
        <span className="text-slate-700">Calculate delivery price</span>
      </nav>

      <FeeEstimateWidget
        zones={zones}
        specialHandling={specialHandling}
        coverageError={error}
        map={mapConfig()}
      />

      {/* Coverage & Service Zones Details */}
      <section className="rounded-2xl bg-white p-5 shadow-xs ring-1 ring-slate-200 sm:p-6 space-y-4">
        <div className="flex flex-wrap items-center justify-between gap-2 border-b border-slate-100 pb-3">
          <div>
            <h2 className="font-semibold text-slate-900 text-sm">Where we deliver across Sri Lanka</h2>
            <p className="text-xs text-slate-500">
              Island-wide coverage across all 9 provinces and 25 administrative districts
            </p>
          </div>
          <span className="status-pill-neutral">
            {zones.length > 0 ? `${zones.length} active service zones` : "Coverage active"}
          </span>
        </div>

        {zones.length === 0 ? (
          <p className="text-xs text-slate-500">Our coverage list is temporarily updating.</p>
        ) : (
          <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
            {zones.map((zone) => (
              <div key={zone.code} className="rounded-xl border border-slate-100 bg-slate-50/60 p-3 text-xs">
                <div className="flex items-center justify-between">
                  <span className="font-semibold text-slate-900">{zone.name} Province</span>
                  <span className="status-pill-neutral font-mono">{zone.code}</span>
                </div>
                <p className="mt-1.5 text-slate-500">{zone.districts.join(", ")}</p>
              </div>
            ))}
          </div>
        )}
      </section>

      <p className="text-center text-xs text-slate-400">
        Estimates are held for 15 minutes. The price confirmed at booking is the price you pay.
      </p>
    </div>
  );
}
