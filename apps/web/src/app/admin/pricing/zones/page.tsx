import { PricingForm } from "@/components/pricing-form";
import { updateSizeClass } from "@/lib/pricing-actions";
import { fetchAdminZones, fetchDistrictAssignments, fetchSizeClasses } from "@/lib/pricing-api";
import { sizeClassLabel } from "@/lib/pricing";

export const metadata = { title: "Zones & districts" };

/**
 * The fixed zone map (read-only) and the size-class multipliers.
 */
export default async function ZonesAdminPage() {
  const [zones, assignments, sizeClasses] = await Promise.all([
    fetchAdminZones(),
    fetchDistrictAssignments(),
    fetchSizeClasses(),
  ]);

  return (
    <div className="space-y-6">
      <div className="space-y-1">
        <h1 className="text-2xl font-bold">Zones &amp; districts</h1>
        <p className="text-slate-600">
          {zones.length} zones covering {assignments.length} districts. The nine provinces, their districts and the
          district-to-zone map are fixed, so this list is read-only. Rate cards are priced per zone pair.
        </p>
      </div>

      <div className="grid gap-6 lg:grid-cols-2">
        <section className="card space-y-3">
          <h2 className="font-semibold">Zones</h2>
          <ul className="divide-y divide-slate-100 text-sm">
            {zones.map((zone) => (
              <li key={zone.code} className="space-y-2 py-3">
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <span className="font-medium">{zone.name}</span>
                  <span className="flex items-center gap-2">
                    <span className="status-pill-neutral">{zone.code}</span>
                    <span
                      className={
                        zone.active
                          ? "status-pill-success"
                          : "status-pill-neutral"
                      }
                    >
                      {zone.active ? "Active" : "Inactive"}
                    </span>
                  </span>
                </div>
                <p className="text-xs text-slate-500">
                  {zone.districts.length > 0 ? zone.districts.join(", ") : "No districts assigned"}
                </p>
              </li>
            ))}
          </ul>
        </section>

        <div className="space-y-6">
          <section className="card space-y-3">
            <h2 className="font-semibold">Size multipliers</h2>
            <p className="text-sm text-slate-600">
              Applied to the base and weight subtotal. A multiplier of 1 adds nothing.
            </p>
            <ul className="divide-y divide-slate-100 text-sm">
              {sizeClasses.map((size) => (
                <li key={size.code} className="space-y-2 py-3">
                  <div className="flex items-center justify-between gap-2">
                    <span className="font-medium">
                      {sizeClassLabel(size.code)} ({size.code})
                    </span>
                    <span className="tabular-nums text-slate-600">x{size.multiplier}</span>
                  </div>
                  <p className="text-xs text-slate-500">
                    Up to {size.maxWeightKg} kg, {size.maxDimensionsCm} cm
                  </p>
                  <PricingForm action={updateSizeClass} submitLabel="Save" className="space-y-2">
                    <input type="hidden" name="code" value={size.code} />
                    <div className="grid grid-cols-2 gap-2 sm:grid-cols-4">
                      <span>
                        <label className="block text-xs font-medium text-slate-700" htmlFor={`multiplier-${size.code}`}>
                          Multiplier
                        </label>
                        <input
                          id={`multiplier-${size.code}`}
                          name="multiplier"
                          className="input"
                          type="number"
                          min="1"
                          step="0.001"
                          defaultValue={size.multiplier}
                          required
                        />
                      </span>
                      {([
                        ["maxLengthCm", "Length", size.maxLengthCm],
                        ["maxWidthCm", "Width", size.maxWidthCm],
                        ["maxHeightCm", "Height", size.maxHeightCm],
                      ] as const).map(([field, label, current]) => (
                        <span key={field}>
                          <label
                            className="block text-xs font-medium text-slate-700"
                            htmlFor={`${field}-${size.code}`}
                          >
                            {label} (cm)
                          </label>
                          <input
                            id={`${field}-${size.code}`}
                            name={field}
                            className="input"
                            type="number"
                            min="0.1"
                            step="0.1"
                            defaultValue={current}
                          />
                        </span>
                      ))}
                    </div>
                  </PricingForm>
                </li>
              ))}
            </ul>
          </section>
        </div>
      </div>

      <p className="text-xs text-slate-500">
        Changes apply to new quotes only. Estimates already issued keep the price the customer was shown.
      </p>
    </div>
  );
}
