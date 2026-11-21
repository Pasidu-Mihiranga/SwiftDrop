import Link from "next/link";
import { CloseRateForm } from "@/components/close-rate-form";
import { PricingForm } from "@/components/pricing-form";
import { RateMatrixGrid } from "@/components/rate-matrix-grid";
import { createRateCard } from "@/lib/pricing-actions";
import { fetchAdminZones, fetchRateCards, fetchRateCardsInForce } from "@/lib/pricing-api";
import {
  buildRateMatrix,
  describeWeightBands,
  formatEffectivePeriod,
  formatMoney,
  formatPercent,
  isInForceOn,
  isoDate,
  laneLabel,
} from "@/lib/pricing";

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

  const [zones, cards, inForce] = await Promise.all([
    fetchAdminZones(),
    fetchRateCards(`?${query}`),
    fetchRateCardsInForce(today),
  ]);
  const { page } = cards;
  const matrix = buildRateMatrix(zones, inForce, today);
  const zoneOptions = zones.filter((zone) => zone.active);
  const pricedLanesCount = matrix.rows.reduce(
    (total, row) => total + row.cells.filter((cell) => cell.card !== undefined).length,
    0,
  );

  return (
    <div className="space-y-6">
      {/* Header and Quick Actions */}
      <div className="flex flex-wrap items-center justify-between gap-4 border-b border-slate-200 pb-5">
        <div className="space-y-1">
          <nav aria-label="Breadcrumb" className="text-xs text-slate-500">
            <Link href="/admin" className="hover:text-slate-800">
              Admin
            </Link>
            <span className="mx-1.5 text-slate-300">/</span>
            <Link href="/admin/pricing/zones" className="hover:text-slate-800">
              Pricing
            </Link>
            <span className="mx-1.5 text-slate-300">/</span>
            <span className="text-slate-700">Rate cards</span>
          </nav>
          <h1 className="text-2xl font-bold tracking-tight text-slate-900 sm:text-3xl">Rate Cards</h1>
          <p className="text-sm text-slate-600">
            Manage versioned delivery rate cards per zone pair across Sri Lanka. Rates apply from start date until exclusive end date.
          </p>
        </div>
        <div className="flex items-center gap-2">
          <a href="#publish" className="btn-primary text-xs shadow-xs">
            + Publish New Rate Card
          </a>
        </div>
      </div>

      {/* KPI Overview Metrics */}
      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
        <div className="rounded-xl border border-slate-200 bg-white p-4 shadow-2xs">
          <p className="text-xs font-medium text-slate-500">Total Rate Cards</p>
          <p className="mt-1 text-2xl font-bold tabular-nums text-slate-900">{page.totalElements}</p>
          <p className="mt-1 text-[11px] text-slate-400">All versions in database</p>
        </div>

        <div className="rounded-xl border border-slate-200 bg-white p-4 shadow-2xs">
          <p className="text-xs font-medium text-slate-500">Active Service Zones</p>
          <p className="mt-1 text-2xl font-bold tabular-nums text-brand-600">{zoneOptions.length}</p>
          <p className="mt-1 text-[11px] text-slate-400">Provinces available for routing</p>
        </div>

        <div className="rounded-xl border border-slate-200 bg-white p-4 shadow-2xs">
          <p className="text-xs font-medium text-slate-500">Lanes In Force Today</p>
          <p className="mt-1 text-2xl font-bold tabular-nums text-emerald-600">{pricedLanesCount}</p>
          <p className="mt-1 text-[11px] text-slate-400">Active zone pair pricing</p>
        </div>

        <div className={`rounded-xl border p-4 shadow-2xs ${matrix.unpriced > 0 ? "border-amber-200 bg-amber-50/40" : "border-slate-200 bg-white"}`}>
          <p className="text-xs font-medium text-slate-500">Unpriced Lanes</p>
          <p className={`mt-1 text-2xl font-bold tabular-nums ${matrix.unpriced > 0 ? "text-amber-700" : "text-emerald-600"}`}>
            {matrix.unpriced}
          </p>
          <p className="mt-1 text-[11px] text-slate-400">
            {matrix.unpriced > 0 ? "Cannot be quoted online" : "All routes covered"}
          </p>
        </div>
      </div>

      {/* Section 1: Rate Matrix Grid (In Force Today) */}
      <section className="card space-y-4">
        <div className="flex flex-wrap items-center justify-between gap-2 border-b border-slate-100 pb-3">
          <div>
            <h2 className="text-base font-semibold text-slate-900">Rates in force today</h2>
            <p className="text-xs text-slate-500">
              Origin zones run down the left and destination zones across the top. Click any lane to view history or update rates.
            </p>
          </div>
          <span className="status-pill-neutral font-mono text-[11px]">
            {today}
          </span>
        </div>

        {matrix.unpriced > 0 ? (
          <p className="notice-error text-xs">
            {matrix.unpriced} {matrix.unpriced === 1 ? "lane has" : "lanes have"} no rate in force and cannot be quoted.
          </p>
        ) : null}

        <RateMatrixGrid matrix={matrix} />
      </section>

      {/* Section 2: Filter Directory */}
      <section className="card space-y-4">
        <div className="flex items-center justify-between border-b border-slate-100 pb-3">
          <h2 className="text-base font-semibold text-slate-900">Filter published rate cards</h2>
        </div>
        <form className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4" method="get">
          <div>
            <label className="block text-xs font-medium text-slate-700 mb-1" htmlFor="fromZone">
              From zone
            </label>
            <select id="fromZone" name="fromZone" className="input text-xs" defaultValue={filters.fromZone ?? ""}>
              <option value="">Any</option>
              {zoneOptions.map((zone) => (
                <option key={zone.code} value={zone.code}>
                  {zone.code} - {zone.name}
                </option>
              ))}
            </select>
          </div>
          <div>
            <label className="block text-xs font-medium text-slate-700 mb-1" htmlFor="toZone">
              To zone
            </label>
            <select id="toZone" name="toZone" className="input text-xs" defaultValue={filters.toZone ?? ""}>
              <option value="">Any</option>
              {zoneOptions.map((zone) => (
                <option key={zone.code} value={zone.code}>
                  {zone.code} - {zone.name}
                </option>
              ))}
            </select>
          </div>
          <div>
            <label className="block text-xs font-medium text-slate-700 mb-1" htmlFor="activeOn">
              In force on date
            </label>
            <input
              id="activeOn"
              name="activeOn"
              type="date"
              className="input text-xs"
              defaultValue={filters.activeOn ?? ""}
            />
          </div>
          <div className="flex items-end gap-2">
            <button className="btn-primary text-xs py-2 px-4 shadow-xs" type="submit">
              Apply Filter
            </button>
            <Link className="btn-secondary text-xs py-2 px-3 shadow-xs" href="/admin/pricing/rate-cards">
              Clear
            </Link>
          </div>
        </form>
      </section>

      {/* Section 3: Published Rate Cards Directory */}
      <section className="card space-y-4">
        <div className="flex items-center justify-between border-b border-slate-100 pb-3">
          <div className="flex items-center gap-2">
            <h2 className="text-base font-semibold text-slate-900">Published rates</h2>
            <span className="status-pill-neutral font-mono">{cards.content.length} results</span>
          </div>
        </div>

        {cards.content.length === 0 ? (
          <p className="notice-info text-xs">
            No rate cards match this filter. Lanes with no card in force cannot be quoted.
          </p>
        ) : (
          <div className="space-y-3">
            {cards.content.map((card) => {
              const current = isInForceOn(card, today);
              const bands = describeWeightBands(card.tiers);
              return (
                <div key={card.id} className="rounded-xl border border-slate-200/90 bg-slate-50/30 p-4 space-y-3 transition hover:border-slate-300 hover:bg-slate-50/70">
                  <div className="flex flex-wrap items-center justify-between gap-2 border-b border-slate-100 pb-2">
                    <div className="flex items-center gap-2">
                      <span className="font-bold text-sm text-slate-900">{laneLabel(card.fromZone, card.toZone)}</span>
                      <span className="text-xs text-slate-400 font-mono">({card.fromZone} &rarr; {card.toZone})</span>
                    </div>
                    <span
                      className={
                        current
                          ? "status-pill-success"
                          : card.effectiveFrom > today
                          ? "status-pill-warning"
                          : "status-pill-neutral"
                      }
                    >
                      {current ? "In force" : card.effectiveFrom > today ? "Scheduled" : "Superseded"}
                    </span>
                  </div>

                  <dl className="grid grid-cols-2 gap-2 text-xs sm:grid-cols-4">
                    <div className="rounded-lg bg-white p-2.5 shadow-2xs border border-slate-100">
                      <dt className="text-slate-400 font-medium text-[11px]">Base fee</dt>
                      <dd className="tabular-nums font-bold text-slate-900 text-sm">{formatMoney(card.baseFee)}</dd>
                    </div>
                    <div className="rounded-lg bg-white p-2.5 shadow-2xs border border-slate-100">
                      <dt className="text-slate-400 font-medium text-[11px]">Per kg fee</dt>
                      <dd className="tabular-nums font-bold text-slate-900 text-sm">
                        {bands ? "See bands" : formatMoney(card.perKgFee)}
                      </dd>
                    </div>
                    <div className="rounded-lg bg-white p-2.5 shadow-2xs border border-slate-100">
                      <dt className="text-slate-400 font-medium text-[11px]">Express multiplier</dt>
                      <dd className="tabular-nums font-bold text-slate-900 text-sm">{`x${card.expressMultiplier}`}</dd>
                    </div>
                    <div className="rounded-lg bg-white p-2.5 shadow-2xs border border-slate-100">
                      <dt className="text-slate-400 font-medium text-[11px]">COD fee fraction</dt>
                      <dd className="tabular-nums font-bold text-slate-900 text-sm">{formatPercent(card.codFeePct)}</dd>
                    </div>
                  </dl>

                  {bands ? (
                    <p className="text-xs text-slate-600 bg-white rounded-lg p-2 border border-slate-100">
                      <span className="font-semibold text-slate-700">Tiered Weight Bands:</span> {bands}
                    </p>
                  ) : null}

                  <div className="flex flex-wrap items-center justify-between gap-2 pt-1 text-xs">
                    <p className="text-slate-500 font-mono text-[11px]">
                      Effective: {formatEffectivePeriod(card.effectiveFrom, card.effectiveTo)}
                    </p>
                    <CloseRateForm rateCardId={card.id} effectiveFrom={card.effectiveFrom} closed={Boolean(card.effectiveTo)} />
                  </div>
                </div>
              );
            })}
          </div>
        )}

        {page.totalPages > 1 ? (
          <nav aria-label="Pages" className="flex items-center justify-center gap-2 pt-2 text-xs">
            <PageLink filters={filters} page={page.page - 1} disabled={page.page === 0} label="&larr; Previous" />
            <span className="text-slate-600 font-medium px-2">
              Page {page.page + 1} of {page.totalPages}
            </span>
            <PageLink
              filters={filters}
              page={page.page + 1}
              disabled={page.page + 1 >= page.totalPages}
              label="Next &rarr;"
            />
          </nav>
        ) : null}
      </section>

      {/* Section 4: Publish New Rate Card Form */}
      <section id="publish" className="card space-y-4">
        <div className="border-b border-slate-100 pb-3">
          <h2 className="text-base font-semibold text-slate-900">Publish a new rate card</h2>
          <p className="text-xs text-slate-500">
            Publishing creates a new version from the chosen effective date without mutating historical cards.
          </p>
        </div>

        <PricingForm key={`${filters.fromZone ?? ""}-${filters.toZone ?? ""}`} action={createRateCard} submitLabel="Publish rate">
          <div className="space-y-4">
            <div className="grid gap-4 sm:grid-cols-2">
              <div>
                <label className="block text-xs font-semibold text-slate-700 mb-1" htmlFor="newFromZone">
                  From zone (Origin) *
                </label>
                <select id="newFromZone" name="fromZone" className="input text-xs" defaultValue={filters.fromZone ?? ""} required>
                  <option value="">Select origin zone</option>
                  {zoneOptions.map((zone) => (
                    <option key={zone.code} value={zone.code}>
                      {zone.code} - {zone.name}
                    </option>
                  ))}
                </select>
              </div>
              <div>
                <label className="block text-xs font-semibold text-slate-700 mb-1" htmlFor="newToZone">
                  To zone (Destination) *
                </label>
                <select id="newToZone" name="toZone" className="input text-xs" defaultValue={filters.toZone ?? ""} required>
                  <option value="">Select destination zone</option>
                  {zoneOptions.map((zone) => (
                    <option key={zone.code} value={zone.code}>
                      {zone.code} - {zone.name}
                    </option>
                  ))}
                </select>
              </div>
            </div>

            <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
              <div>
                <label className="block text-xs font-semibold text-slate-700 mb-1" htmlFor="baseFee">
                  Base fee (LKR) *
                </label>
                <input id="baseFee" name="baseFee" type="number" min="0" step="0.01" placeholder="e.g. 475.00" className="input text-xs" required />
              </div>
              <div>
                <label className="block text-xs font-semibold text-slate-700 mb-1" htmlFor="perKgFee">
                  Per kilogram (LKR) *
                </label>
                <input id="perKgFee" name="perKgFee" type="number" min="0" step="0.01" placeholder="e.g. 60.00" className="input text-xs" required />
              </div>
              <div>
                <label className="block text-xs font-semibold text-slate-700 mb-1" htmlFor="expressMultiplier">
                  Express multiplier *
                </label>
                <input
                  id="expressMultiplier"
                  name="expressMultiplier"
                  type="number"
                  min="1"
                  step="0.001"
                  defaultValue="1.5"
                  className="input text-xs"
                  required
                />
              </div>
              <div>
                <label className="block text-xs font-semibold text-slate-700 mb-1" htmlFor="codFeePct">
                  COD fraction (0.015 = 1.5%) *
                </label>
                <input
                  id="codFeePct"
                  name="codFeePct"
                  type="number"
                  min="0"
                  max="1"
                  step="0.0001"
                  defaultValue="0.015"
                  className="input text-xs"
                  required
                />
              </div>
            </div>

            <div className="grid gap-4 sm:grid-cols-2">
              <div>
                <label className="block text-xs font-semibold text-slate-700 mb-1" htmlFor="effectiveFrom">
                  Takes effect on date *
                </label>
                <input id="effectiveFrom" name="effectiveFrom" type="date" className="input text-xs" defaultValue={today} required />
              </div>
              <div>
                <label className="block text-xs font-semibold text-slate-700 mb-1" htmlFor="effectiveTo">
                  Stops on date (optional)
                </label>
                <input id="effectiveTo" name="effectiveTo" type="date" className="input text-xs" />
              </div>
            </div>

            <fieldset className="rounded-xl border border-slate-200 bg-slate-50/60 p-4 space-y-3">
              <legend className="text-xs font-semibold text-slate-800 px-1">Marginal weight bands (optional)</legend>
              <p className="text-xs text-slate-500">
                Each rate applies only to the kilograms inside its band. Leave blank if standard per-kilogram rate applies uniformly.
              </p>
              <div className="grid gap-2 sm:grid-cols-2">
                {[1, 2, 3, 4].map((row) => (
                  <div key={row} className="grid grid-cols-2 gap-2">
                    <div>
                      <label className="block text-[11px] font-medium text-slate-600 mb-0.5" htmlFor={`bandUpTo${row}`}>
                        Band {row} up to (kg)
                      </label>
                      <input id={`bandUpTo${row}`} name={`bandUpTo${row}`} type="number" min="0.01" step="0.01" className="input text-xs" />
                    </div>
                    <div>
                      <label className="block text-[11px] font-medium text-slate-600 mb-0.5" htmlFor={`bandRate${row}`}>
                        Per kg (LKR)
                      </label>
                      <input id={`bandRate${row}`} name={`bandRate${row}`} type="number" min="0" step="0.01" className="input text-xs" />
                    </div>
                  </div>
                ))}
              </div>
              <div className="sm:w-1/2">
                <label className="block text-[11px] font-medium text-slate-600 mb-0.5" htmlFor="bandRateAbove">
                  Above last band rate (LKR / kg)
                </label>
                <input id="bandRateAbove" name="bandRateAbove" type="number" min="0" step="0.01" className="input text-xs" />
              </div>
            </fieldset>
          </div>
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
