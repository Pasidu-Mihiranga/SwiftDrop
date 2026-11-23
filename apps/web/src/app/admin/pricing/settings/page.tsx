import { PricingForm } from "@/components/pricing-form";
import {
  addSpecialHandling,
  publishDistanceBands,
  publishPricingSettings,
  updateSameDayVehicle,
  updateSpecialHandling,
} from "@/lib/pricing-actions";
import {
  fetchDistanceBands,
  fetchPricingSettings,
  fetchSameDayVehicles,
  fetchSpecialHandlingConfig,
} from "@/lib/pricing-api";
import {
  bandSets,
  describeBandRange,
  formatCutoff,
  formatEffectivePeriod,
  formatMoney,
  formatPercent,
  fractionToPercentText,
  isoDate,
  periodStatus,
  settingsInForce,
  type DistanceBandVersion,
} from "@/lib/pricing";

export const metadata = { title: "Fees & handling" };

/** Enough rows for any sensible set; blank rows are ignored, so the form works without scripting. */
const BAND_ROWS = [1, 2, 3, 4, 5, 6, 7, 8];

const LABEL = "block text-sm font-medium text-slate-700";

/**
 * The configuration that applies to every quote rather than to one lane: fuel, tax, the minimum
 * charge and the same-day cut-off, the line-haul distance bands, the special handling options and
 * the same-day vehicles.
 *
 * Settings and bands are versioned like rate cards: a change is published from a date and closes
 * what it replaces. Handling options and vehicles are edited in place; a quote already issued keeps
 * the fee it was given either way.
 */
export default async function PricingSettingsAdminPage() {
  const [settings, bands, handling, vehicles] = await Promise.all([
    fetchPricingSettings(),
    fetchDistanceBands(),
    fetchSpecialHandlingConfig(),
    fetchSameDayVehicles(),
  ]);
  const today = isoDate();
  const current = settingsInForce(settings, today);
  const sets = bandSets(bands);
  const currentSet = sets.find((set) => periodStatus(set, today) === "In force");

  return (
    <div className="space-y-6">
      <div className="space-y-1">
        <h1 className="text-2xl font-bold">Fees &amp; handling</h1>
        <p className="text-slate-600">
          What every quote pays on top of its lane: fuel, tax, the minimum charge and line haul, plus the extras a
          customer can choose. Changes apply to new quotes only.
        </p>
      </div>

      <section className="card space-y-4" aria-labelledby="fee-settings">
        <div className="space-y-1">
          <h2 id="fee-settings" className="font-semibold">Fee settings</h2>
          {current ? (
            <dl className="grid gap-3 text-sm sm:grid-cols-4">
              <Fact label="Fuel surcharge" value={formatPercent(current.fuelSurchargePct)} />
              <Fact label="Tax" value={formatPercent(current.taxPct)} />
              <Fact label="Minimum charge" value={formatMoney(current.minimumCharge)} />
              <Fact label="Same-day cut-off" value={formatCutoff(current.sameDayCutoff)} />
            </dl>
          ) : (
            <p className="notice-info">No fee settings are in force today, so no fuel, tax or minimum is applied.</p>
          )}
        </div>

        <ul className="divide-y divide-slate-100 text-sm">
          {settings.map((version) => {
            const status = periodStatus(version, today);
            return (
              <li key={version.id} className="flex flex-wrap items-center justify-between gap-2 py-2">
                <span className="text-slate-600">
                  {formatPercent(version.fuelSurchargePct)} fuel, {formatPercent(version.taxPct)} tax, minimum{" "}
                  {formatMoney(version.minimumCharge)}, same-day {formatCutoff(version.sameDayCutoff).toLowerCase()}
                </span>
                <span className="flex items-center gap-2 text-xs text-slate-500">
                  {formatEffectivePeriod(version.effectiveFrom, version.effectiveTo)}
                  <span className={status === "In force" ? "status-pill-success" : "status-pill-neutral"}>{status}</span>
                </span>
              </li>
            );
          })}
        </ul>

        <PricingForm action={publishPricingSettings} submitLabel="Publish settings">
          <div className="grid gap-3 sm:grid-cols-5">
            <span>
              <label className={LABEL} htmlFor="fuelPercent">Fuel (%)</label>
              <input id="fuelPercent" name="fuelPercent" type="number" min="0" max="99.99" step="0.01" className="input"
                defaultValue={current ? fractionToPercentText(current.fuelSurchargePct) : ""} required />
            </span>
            <span>
              <label className={LABEL} htmlFor="taxPercent">Tax (%)</label>
              <input id="taxPercent" name="taxPercent" type="number" min="0" max="99.99" step="0.01" className="input"
                defaultValue={current ? fractionToPercentText(current.taxPct) : ""} required />
            </span>
            <span>
              <label className={LABEL} htmlFor="minimumCharge">Minimum (LKR)</label>
              <input id="minimumCharge" name="minimumCharge" type="number" min="0" step="0.01" className="input"
                defaultValue={current?.minimumCharge ?? ""} required />
            </span>
            <span>
              <label className={LABEL} htmlFor="sameDayCutoff">Same-day cut-off</label>
              <input id="sameDayCutoff" name="sameDayCutoff" type="time" className="input"
                defaultValue={current?.sameDayCutoff?.slice(0, 5) ?? ""} />
            </span>
            <span>
              <label className={LABEL} htmlFor="settingsFrom">Takes effect on</label>
              <input id="settingsFrom" name="effectiveFrom" type="date" min={today} className="input" required />
            </span>
          </div>
          <p className="text-xs text-slate-500">
            The cut-off is local time: from then on same-day is refused for that day. Leave it blank to take same-day
            orders at any time. The date must be today or later and after the newest version above.
          </p>
        </PricingForm>
      </section>

      <section className="card space-y-4" aria-labelledby="distance-bands">
        <div className="space-y-1">
          <h2 id="distance-bands" className="font-semibold">Line-haul distance bands</h2>
          <p className="text-sm text-slate-600">
            Charged on the road distance for standard and express (same-day is priced per kilometre instead), and they
            set the transit time the quote shows.
          </p>
        </div>

        {sets.length === 0 ? (
          <p className="notice-info">No distance bands are configured, so no line haul is charged.</p>
        ) : (
          <div className="space-y-4">
            {sets.map((set) => (
              <BandSetTable key={set.effectiveFrom} bands={set.bands}
                caption={`${formatEffectivePeriod(set.effectiveFrom, set.effectiveTo)} (${periodStatus(set, today)})`} />
            ))}
          </div>
        )}

        <PricingForm action={publishDistanceBands} submitLabel="Publish bands">
          <p className="text-sm text-slate-600">
            The first band starts at 0 km and each one starts where the previous ends. Leave the last band&apos;s end
            blank so every longer trip is priced. Blank rows are ignored.
          </p>
          <div className="space-y-2">
            {BAND_ROWS.map((row) => {
              const band: DistanceBandVersion | undefined = currentSet?.bands[row - 1];
              return (
                <fieldset key={row} className="grid gap-2 sm:grid-cols-4">
                  <legend className="sr-only">Band {row}</legend>
                  <input aria-label={`Band ${row} ends at (km)`} name={`bandUpTo${row}`} type="number" min="0.01"
                    step="0.01" placeholder={row === 1 ? "Ends at km (blank if last)" : "Ends at km"} className="input"
                    defaultValue={band?.maxKm ?? ""} />
                  <input aria-label={`Band ${row} line-haul fee (LKR)`} name={`bandFee${row}`} type="number" min="0"
                    step="0.01" placeholder="Fee (LKR)" className="input" defaultValue={band?.lineHaulFee ?? ""} />
                  <input aria-label={`Band ${row} standard days`} name={`bandStandard${row}`} type="number" min="1"
                    max="30" step="1" placeholder="Standard days" className="input"
                    defaultValue={band?.standardTransitDays ?? ""} />
                  <input aria-label={`Band ${row} express days`} name={`bandExpress${row}`} type="number" min="1"
                    max="30" step="1" placeholder="Express days" className="input"
                    defaultValue={band?.expressTransitDays ?? ""} />
                </fieldset>
              );
            })}
          </div>
          <span className="block sm:w-1/4">
            <label className={LABEL} htmlFor="bandsFrom">Takes effect on</label>
            <input id="bandsFrom" name="effectiveFrom" type="date" min={today} className="input" required />
          </span>
        </PricingForm>
      </section>

      <section className="card space-y-4" aria-labelledby="special-handling">
        <div className="space-y-1">
          <h2 id="special-handling" className="font-semibold">Special handling</h2>
          <p className="text-sm text-slate-600">
            Extras customers can tick on the quote form, each a flat fee. Retiring an option hides it from new quotes.
          </p>
        </div>

        <ul className="divide-y divide-slate-100">
          {handling.map((option) => (
            <li key={option.code} className="py-3">
              <PricingForm action={updateSpecialHandling} submitLabel="Save" className="grid items-end gap-2 sm:grid-cols-6">
                <input type="hidden" name="code" value={option.code} />
                <span className="text-sm font-medium sm:pb-2">
                  {option.code}
                  {option.active ? null : <span className="status-pill-neutral ml-2">Retired</span>}
                </span>
                <span className="sm:col-span-2">
                  <label className={LABEL} htmlFor={`label-${option.code}`}>Label</label>
                  <input id={`label-${option.code}`} name="label" className="input" maxLength={100}
                    defaultValue={option.label} required />
                </span>
                <span>
                  <label className={LABEL} htmlFor={`fee-${option.code}`}>Fee (LKR)</label>
                  <input id={`fee-${option.code}`} name="fee" type="number" min="0" step="0.01" className="input"
                    defaultValue={option.fee} required />
                </span>
                <span>
                  <label className={LABEL} htmlFor={`sort-${option.code}`}>Order</label>
                  <input id={`sort-${option.code}`} name="sortOrder" type="number" min="0" max="9999" step="1"
                    className="input" defaultValue={option.sortOrder} />
                </span>
                <label className="flex items-center gap-2 text-sm text-slate-700 sm:pb-2">
                  <input type="checkbox" name="active" defaultChecked={option.active} />
                  Offered
                </label>
              </PricingForm>
            </li>
          ))}
        </ul>

        <PricingForm action={addSpecialHandling} submitLabel="Add option">
          <h3 className="text-sm font-semibold">Add an option</h3>
          <div className="grid gap-3 sm:grid-cols-4">
            <span>
              <label className={LABEL} htmlFor="newCode">Code</label>
              <input id="newCode" name="code" className="input" maxLength={32} pattern="[A-Za-z][A-Za-z0-9_]*"
                placeholder="OVERSIZE" required />
            </span>
            <span>
              <label className={LABEL} htmlFor="newLabel">Label</label>
              <input id="newLabel" name="label" className="input" maxLength={100} required />
            </span>
            <span>
              <label className={LABEL} htmlFor="newFee">Fee (LKR)</label>
              <input id="newFee" name="fee" type="number" min="0" step="0.01" className="input" required />
            </span>
            <span>
              <label className={LABEL} htmlFor="newSort">Order</label>
              <input id="newSort" name="sortOrder" type="number" min="0" max="9999" step="1" className="input" />
            </span>
          </div>
          <p className="text-xs text-slate-500">The code is permanent and is what quotes record; the label can change.</p>
        </PricingForm>
      </section>

      <section className="card space-y-4" aria-labelledby="vehicles">
        <div className="space-y-1">
          <h2 id="vehicles" className="font-semibold">Same-day vehicles</h2>
          <p className="text-sm text-slate-600">
            A same-day job outside a vehicle&apos;s limits is refused, not moved to a bigger vehicle. The multiplier
            scales the per-kilometre distance charge.
          </p>
        </div>
        <ul className="divide-y divide-slate-100">
          {vehicles.map((vehicle) => (
            <li key={vehicle.code} className="py-3">
              <PricingForm action={updateSameDayVehicle} submitLabel="Save" className="grid items-end gap-2 sm:grid-cols-6">
                <input type="hidden" name="code" value={vehicle.code} />
                <span>
                  <label className={LABEL} htmlFor={`vlabel-${vehicle.code}`}>{vehicle.code}</label>
                  <input id={`vlabel-${vehicle.code}`} name="label" className="input" maxLength={60}
                    defaultValue={vehicle.label} required />
                </span>
                <span>
                  <label className={LABEL} htmlFor={`weight-${vehicle.code}`}>Max kg</label>
                  <input id={`weight-${vehicle.code}`} name="maxWeightKg" type="number" min="0.01" max="100" step="0.01"
                    className="input" defaultValue={vehicle.maxWeightKg} required />
                </span>
                <span>
                  <label className={LABEL} htmlFor={`distance-${vehicle.code}`}>Max km</label>
                  <input id={`distance-${vehicle.code}`} name="maxDistanceKm" type="number" min="0.01" step="0.01"
                    className="input" defaultValue={vehicle.maxDistanceKm} required />
                </span>
                <span>
                  <label className={LABEL} htmlFor={`multiplier-${vehicle.code}`}>Rate x</label>
                  <input id={`multiplier-${vehicle.code}`} name="rateMultiplier" type="number" min="0.001" max="99"
                    step="0.001" className="input" defaultValue={vehicle.rateMultiplier} required />
                </span>
                <label className="flex items-center gap-2 text-sm text-slate-700 sm:pb-2">
                  <input type="checkbox" name="active" defaultChecked={vehicle.active} />
                  Offered
                </label>
              </PricingForm>
            </li>
          ))}
        </ul>
      </section>
    </div>
  );
}

function Fact({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-lg bg-slate-50 p-3">
      <dt className="text-xs text-slate-500">{label}</dt>
      <dd className="font-semibold tabular-nums">{value}</dd>
    </div>
  );
}

function BandSetTable({ bands, caption }: { bands: DistanceBandVersion[]; caption: string }) {
  return (
    <div className="overflow-x-auto">
      <table className="w-full text-left text-sm">
        <caption className="pb-1 text-left text-xs text-slate-500">{caption}</caption>
        <thead className="text-xs text-slate-500">
          <tr>
            <th scope="col" className="py-1 pr-3 font-medium">Distance</th>
            <th scope="col" className="py-1 pr-3 font-medium">Line haul</th>
            <th scope="col" className="py-1 pr-3 font-medium">Standard</th>
            <th scope="col" className="py-1 font-medium">Express</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-100">
          {bands.map((band) => (
            <tr key={band.id}>
              <td className="py-1 pr-3">{describeBandRange(band)}</td>
              <td className="py-1 pr-3 tabular-nums">{formatMoney(band.lineHaulFee)}</td>
              <td className="py-1 pr-3">{band.standardTransitDays} d</td>
              <td className="py-1">{band.expressTransitDays} d</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
