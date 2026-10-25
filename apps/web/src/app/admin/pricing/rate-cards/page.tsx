import Link from "next/link";
import { PricingForm } from "@/components/pricing-form";
import { closeRateCard, createRateCard } from "@/lib/pricing-actions";
import { fetchAdminZones, fetchRateCards } from "@/lib/pricing-api";
import { formatEffectivePeriod, formatMoney, formatPercent, isInForceOn, isoDate, laneLabel } from "@/lib/pricing";

export const metadata = { title: "Rate cards" };

interface SearchParams {
  fromZone?: string;
  toZone?: string;
  activeOn?: string;
  page?: string;
}

/**
 * Rate cards per zone pair, versioned by effective date.
 *
 * Nothing is edited in place: changing a price means closing the card in force and adding a new
 * one from the changeover date. That is what lets an estimate a customer already holds stay valid.
 */
export default async function RateCardsAdminPage({
  searchParams,
}: {
  searchParams: Promise<SearchParams>;
}) {
  const filters = await searchParams;
  const today = isoDate();
  const query = new URLSearchParams();
  if (filters.fromZone) query.set("fromZone", filters.fromZone);
  if (filters.toZone) query.set("toZone", filters.toZone);
  if (filters.activeOn) query.set("activeOn", filters.activeOn);
  query.set("page", filters.page ?? "0");
  query.set("size", "20");

  const [zones, cards] = await Promise.all([fetchAdminZones(), fetchRateCards(`?${query}`)]);
  const { page } = cards;
  const zoneOptions = zones.filter((zone) => zone.active);

  return (
    <div className="space-y-6">
      <div className="space-y-1">
        <h1 className="text-2xl font-bold">Rate cards</h1>
        <p className="text-slate-600">
          {page.totalElements} cards across {zones.length} zones. A card applies from its start date until its end date,
          which is exclusive.
        </p>
      </div>

      <section className="card space-y-3">
        <h2 className="font-semibold">Filter</h2>
        <form className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4" method="get">
          <span>
            <label className="block text-sm font-medium text-slate-700" htmlFor="fromZone">
              From zone
            </label>
            <select id="fromZone" name="fromZone" className="input" defaultValue={filters.fromZone ?? ""}>
              <option value="">Any</option>
              {zoneOptions.map((zone) => (
                <option key={zone.code} value={zone.code}>
                  {zone.code}
                </option>
              ))}
            </select>
          </span>
          <span>
            <label className="block text-sm font-medium text-slate-700" htmlFor="toZone">
              To zone
            </label>
            <select id="toZone" name="toZone" className="input" defaultValue={filters.toZone ?? ""}>
              <option value="">Any</option>
              {zoneOptions.map((zone) => (
                <option key={zone.code} value={zone.code}>
                  {zone.code}
                </option>
              ))}
            </select>
          </span>
          <span>
            <label className="block text-sm font-medium text-slate-700" htmlFor="activeOn">
              In force on
            </label>
            <input
              id="activeOn"
              name="activeOn"
              type="date"
              className="input"
              defaultValue={filters.activeOn ?? ""}
            />
          </span>
          <span className="flex items-end gap-2">
            <button className="btn-primary" type="submit">
              Apply
            </button>
            <Link className="btn-secondary" href="/admin/pricing/rate-cards">
              Clear
            </Link>
          </span>
        </form>
      </section>

      <section className="card space-y-3">
        <h2 className="font-semibold">Published rates</h2>
        {cards.content.length === 0 ? (
          <p className="notice-info">
            No rate cards match this filter. Lanes with no card in force cannot be quoted.
          </p>
        ) : (
          <ul className="divide-y divide-slate-100 text-sm">
            {cards.content.map((card) => {
              const current = isInForceOn(card, today);
              return (
                <li key={card.id} className="space-y-2 py-3">
                  <div className="flex flex-wrap items-center justify-between gap-2">
                    <span className="font-medium">{laneLabel(card.fromZone, card.toZone)}</span>
                    <span
                      className={
                        current
                          ? "status-pill-success"
                          : "status-pill-neutral"
                      }
                    >
                      {current ? "In force" : card.effectiveFrom > today ? "Scheduled" : "Superseded"}
                    </span>
                  </div>
                  <dl className="grid grid-cols-2 gap-x-4 gap-y-1 text-xs text-slate-600 sm:grid-cols-4">
                    <Detail label="Base" value={formatMoney(card.baseFee)} />
                    <Detail label="Per kg" value={formatMoney(card.perKgFee)} />
                    <Detail label="Express" value={`x${card.expressMultiplier}`} />
                    <Detail label="Cash on delivery" value={formatPercent(card.codFeePct)} />
                  </dl>
                  <p className="text-xs text-slate-500">
                    {formatEffectivePeriod(card.effectiveFrom, card.effectiveTo)}
                  </p>
                  {card.effectiveTo ? null : (
                    <details className="text-sm">
                      <summary className="cursor-pointer text-brand-700">Close this rate</summary>
                      <div className="mt-2">
                        <PricingForm action={closeRateCard} submitLabel="Close rate" className="flex flex-col gap-2 sm:flex-row sm:items-end">
                          <input type="hidden" name="rateCardId" value={card.id} />
                          <span className="flex-1">
                            <label className="block text-xs font-medium text-slate-700" htmlFor={`to-${card.id}`}>
                              Stops applying on
                            </label>
                            <input
                              id={`to-${card.id}`}
                              name="effectiveTo"
                              type="date"
                              className="input"
                              min={card.effectiveFrom}
                              required
                            />
                          </span>
                        </PricingForm>
                        <p className="mt-1 text-xs text-slate-500">
                          Closing a rate without adding a replacement leaves the lane unquotable from that date.
                        </p>
                      </div>
                    </details>
                  )}
                </li>
              );
            })}
          </ul>
        )}

        {page.totalPages > 1 ? (
          <nav aria-label="Pages" className="flex items-center gap-2 text-sm">
            <PageLink filters={filters} page={page.page - 1} disabled={page.page === 0} label="Previous" />
            <span className="text-slate-600">
              Page {page.page + 1} of {page.totalPages}
            </span>
            <PageLink
              filters={filters}
              page={page.page + 1}
              disabled={page.page + 1 >= page.totalPages}
              label="Next"
            />
          </nav>
        ) : null}
      </section>

      <section className="card space-y-3">
        <h2 className="font-semibold">Publish a new rate</h2>
        <PricingForm action={createRateCard} submitLabel="Publish rate">
          <div className="grid gap-3 sm:grid-cols-2">
            <span>
              <label className="block text-sm font-medium text-slate-700" htmlFor="newFromZone">
                From zone
              </label>
              <select id="newFromZone" name="fromZone" className="input" required>
                <option value="">Select</option>
                {zoneOptions.map((zone) => (
                  <option key={zone.code} value={zone.code}>
                    {zone.code} - {zone.name}
                  </option>
                ))}
              </select>
            </span>
            <span>
              <label className="block text-sm font-medium text-slate-700" htmlFor="newToZone">
                To zone
              </label>
              <select id="newToZone" name="toZone" className="input" required>
                <option value="">Select</option>
                {zoneOptions.map((zone) => (
                  <option key={zone.code} value={zone.code}>
                    {zone.code} - {zone.name}
                  </option>
                ))}
              </select>
            </span>
            <span>
              <label className="block text-sm font-medium text-slate-700" htmlFor="baseFee">
                Base fee (LKR)
              </label>
              <input id="baseFee" name="baseFee" type="number" min="0" step="0.01" className="input" required />
            </span>
            <span>
              <label className="block text-sm font-medium text-slate-700" htmlFor="perKgFee">
                Per kilogram (LKR)
              </label>
              <input id="perKgFee" name="perKgFee" type="number" min="0" step="0.01" className="input" required />
            </span>
            <span>
              <label className="block text-sm font-medium text-slate-700" htmlFor="expressMultiplier">
                Express multiplier
              </label>
              <input
                id="expressMultiplier"
                name="expressMultiplier"
                type="number"
                min="1"
                step="0.001"
                className="input"
                required
              />
            </span>
            <span>
              <label className="block text-sm font-medium text-slate-700" htmlFor="codFeePct">
                Cash-on-delivery fraction
              </label>
              <input
                id="codFeePct"
                name="codFeePct"
                type="number"
                min="0"
                max="1"
                step="0.0001"
                className="input"
                required
              />
              <span className="text-xs text-slate-500">A fraction, not a percentage: 0.015 is 1.5 per cent.</span>
            </span>
            <span>
              <label className="block text-sm font-medium text-slate-700" htmlFor="effectiveFrom">
                Takes effect on
              </label>
              <input id="effectiveFrom" name="effectiveFrom" type="date" className="input" required />
            </span>
            <span>
              <label className="block text-sm font-medium text-slate-700" htmlFor="effectiveTo">
                Stops on (optional)
              </label>
              <input id="effectiveTo" name="effectiveTo" type="date" className="input" />
            </span>
          </div>
          <p className="text-xs text-slate-500">
            Periods may not overlap for the same lane, so close the current rate first. A rate starting the day another
            ends leaves neither a gap nor an overlap.
          </p>
        </PricingForm>
      </section>
    </div>
  );
}

function Detail({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <dt className="text-slate-500">{label}</dt>
      <dd className="tabular-nums font-medium text-slate-700">{value}</dd>
    </div>
  );
}

function PageLink({
  filters,
  page,
  disabled,
  label,
}: {
  filters: SearchParams;
  page: number;
  disabled: boolean;
  label: string;
}) {
  if (disabled) {
    return <span className="rounded-md px-3 py-2 text-slate-400">{label}</span>;
  }
  const query = new URLSearchParams();
  if (filters.fromZone) query.set("fromZone", filters.fromZone);
  if (filters.toZone) query.set("toZone", filters.toZone);
  if (filters.activeOn) query.set("activeOn", filters.activeOn);
  query.set("page", String(page));
  return (
    <Link className="rounded-md px-3 py-2 text-brand-700 hover:bg-slate-100" href={`/admin/pricing/rate-cards?${query}`}>
      {label}
    </Link>
  );
}
