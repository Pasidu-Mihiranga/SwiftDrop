import Link from "next/link";
import { FeeEstimateWidget } from "@/components/fee-estimate-widget";
import { fetchActiveZones } from "@/lib/pricing-api";

export const metadata = {
  title: "Delivery fee estimate",
  description: "See what a SwiftDrop delivery costs before you sign up.",
};

/** Coverage is read on the server so no zone list or API host ever ships to the browser. */
export default async function QuotePage() {
  const { zones, error } = await fetchActiveZones();

  return (
    <div className="space-y-6">
      <div className="space-y-2">
        <h1 className="text-2xl font-bold">What will my delivery cost?</h1>
        <p className="text-slate-600">
          Get a price for any of our {zones.length > 0 ? `${zones.length} service zones` : "service zones"} across Sri
          Lanka. No account needed.
        </p>
      </div>

      <div className="grid min-w-0 gap-6 lg:grid-cols-[minmax(0,2fr)_minmax(0,1fr)]">
        <div className="card min-w-0">
          <FeeEstimateWidget zones={zones} coverageError={error} />
        </div>

        <aside className="space-y-4">
          <section className="card space-y-3">
            <h2 className="font-semibold">Where we deliver</h2>
            {zones.length === 0 ? (
              <p className="text-sm text-slate-600">Our coverage list is unavailable right now.</p>
            ) : (
              <ul className="divide-y divide-slate-100 text-sm">
                {zones.map((zone) => (
                  <li key={zone.code} className="py-2">
                    <div className="flex items-center justify-between gap-2">
                      <span className="font-medium">{zone.name}</span>
                      <span className="status-pill-neutral">{zone.code}</span>
                    </div>
                    <p className="mt-1 text-xs text-slate-500">{zone.districts.join(", ")}</p>
                  </li>
                ))}
              </ul>
            )}
          </section>

          <section className="card space-y-2">
            <h2 className="font-semibold">Ready to send?</h2>
            <p className="text-sm text-slate-600">
              Sign in to book a collection.
            </p>
            <Link href="/login?callbackUrl=%2Fcustomer" className="btn-primary w-full">
              Sign in to book
            </Link>
          </section>
        </aside>
      </div>

      <p className="text-xs text-slate-500">
        Estimates are held for a few minutes and may change afterwards. The price confirmed at booking is the price you
        pay.
      </p>
    </div>
  );
}
