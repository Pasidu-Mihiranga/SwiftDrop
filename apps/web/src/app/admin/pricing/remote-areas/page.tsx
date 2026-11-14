import { PricingForm } from "@/components/pricing-form";
import { publishRemoteArea } from "@/lib/pricing-actions";
import { fetchDistrictAssignments, fetchRemoteAreas } from "@/lib/pricing-api";
import { formatEffectivePeriod, formatMoney, isInForceOn, isoDate, periodStatus } from "@/lib/pricing";

export const metadata = { title: "Remote areas" };

/**
 * Districts that cost extra to serve. A charge applies at each end of a journey, and like a rate
 * card it is never edited: a new one closes whatever it replaces, so the history stays readable.
 */
export default async function RemoteAreasAdminPage() {
  const [charges, assignments] = await Promise.all([fetchRemoteAreas(), fetchDistrictAssignments()]);
  const today = isoDate();
  const inForce = charges.filter((charge) => isInForceOn(charge, today) && charge.flatFee > 0);

  return (
    <div className="space-y-6">
      <div className="space-y-1">
        <h1 className="text-2xl font-bold">Remote areas</h1>
        <p className="text-slate-600">
          {inForce.length} of {assignments.length} districts carry a charge today. It applies at each end, so a journey
          between two remote districts pays both.
        </p>
      </div>

      <section className="card space-y-3">
        <h2 className="font-semibold">Published charges</h2>
        {charges.length === 0 ? (
          <p className="notice-info">No district carries a remote-area charge yet.</p>
        ) : (
          <ul className="divide-y divide-slate-100 text-sm">
            {charges.map((charge) => {
              const status = periodStatus(charge, today);
              return (
                <li key={`${charge.district}-${charge.effectiveFrom}`} className="space-y-1 py-3">
                  <div className="flex flex-wrap items-center justify-between gap-2">
                    <span className="font-medium">{charge.district}</span>
                    <span className="flex items-center gap-2">
                      <span className="tabular-nums text-slate-600">{formatMoney(charge.flatFee)}</span>
                      <span className={status === "In force" ? "status-pill-success" : "status-pill-neutral"}>{status}</span>
                    </span>
                  </div>
                  <p className="text-xs text-slate-500">
                    {formatEffectivePeriod(charge.effectiveFrom, charge.effectiveTo)}
                  </p>
                </li>
              );
            })}
          </ul>
        )}
      </section>

      <section className="card space-y-3">
        <h2 className="font-semibold">Publish a charge</h2>
        <PricingForm action={publishRemoteArea} submitLabel="Publish charge">
          <div className="grid gap-3 sm:grid-cols-3">
            <span>
              <label className="block text-sm font-medium text-slate-700" htmlFor="district">
                District
              </label>
              <select id="district" name="district" className="input" required>
                <option value="">Select a district</option>
                {assignments.map((assignment) => (
                  <option key={assignment.district} value={assignment.district}>
                    {assignment.district}
                  </option>
                ))}
              </select>
            </span>
            <span>
              <label className="block text-sm font-medium text-slate-700" htmlFor="flatFee">
                Charge (LKR)
              </label>
              <input id="flatFee" name="flatFee" type="number" min="0" step="0.01" className="input" required />
            </span>
            <span>
              <label className="block text-sm font-medium text-slate-700" htmlFor="effectiveFrom">
                Takes effect on
              </label>
              <input id="effectiveFrom" name="effectiveFrom" type="date" className="input" required />
            </span>
          </div>
          <p className="text-xs text-slate-500">
            This replaces the district&apos;s current charge from that date. Publish a charge of 0 to stop charging. To fix
            a mistake, publish again from a later date: periods cannot be rewritten.
          </p>
        </PricingForm>
      </section>
    </div>
  );
}
