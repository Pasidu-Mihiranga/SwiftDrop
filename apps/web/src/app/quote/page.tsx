import Link from "next/link";
import { FeeEstimateWidget } from "@/components/fee-estimate-widget";
import { mapConfig } from "@/lib/map-config";
import { fetchActiveZones, fetchSpecialHandling } from "@/lib/pricing-api";

export const metadata = {
  title: "Delivery fee estimate",
  description: "See what a SwiftDrop delivery costs before you sign up.",
};

/** Coverage is read on the server so no zone list or API host ever ships to the browser. */
export default async function QuotePage() {
  const [{ zones, error }, { options: specialHandling }] = await Promise.all([
    fetchActiveZones(),
    fetchSpecialHandling(),
  ]);

  return (
    <div className="space-y-5">
      <header>
        <nav aria-label="Breadcrumb" className="text-xs text-slate-500">
          <Link href="/" className="hover:text-slate-800">
            SwiftDrop
          </Link>
          <span className="mx-1.5 text-slate-300">/</span>
          <span className="text-slate-700">Pricing &amp; quotes</span>
        </nav>
        <h1 className="mt-1 text-2xl font-semibold tracking-tight text-slate-900 sm:text-3xl">
          What will my delivery cost?
        </h1>
        <p className="mt-1 text-sm text-slate-600">
          Get a price for any of our {zones.length > 0 ? `${zones.length} service zones` : "service zones"} across Sri
          Lanka. No account needed.
        </p>
      </header>

      <div className="grid min-w-0 gap-6 lg:grid-cols-[minmax(0,2fr)_minmax(0,1fr)]">
        <div className="card min-w-0">
          <FeeEstimateWidget
            zones={zones}
            specialHandling={specialHandling}
            coverageError={error}
            map={mapConfig()}
          />
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
