import { PricingForm } from "@/components/pricing-form";
import { assignDistrict, createZone, updateSizeClass, updateZone } from "@/lib/pricing-actions";
import { fetchAdminZones, fetchDistrictAssignments, fetchSizeClasses } from "@/lib/pricing-api";
import { sizeClassLabel } from "@/lib/pricing";

export const metadata = { title: "Zones & districts" };

/**
 * The zone map and the size-class multipliers. Every district must belong to a zone, otherwise it
 * cannot be quoted, so the unassigned count is called out rather than left to be noticed.
 */
export default async function ZonesAdminPage() {
  const [zones, assignments, sizeClasses] = await Promise.all([
    fetchAdminZones(),
    fetchDistrictAssignments(),
    fetchSizeClasses(),
  ]);

  const activeZones = zones.filter((zone) => zone.active);

  return (
    <div className="space-y-6">
      <div className="space-y-1">
        <h1 className="text-2xl font-bold">Zones &amp; districts</h1>
        <p className="text-slate-600">
          {zones.length} zones covering {assignments.length} districts. Rate cards are priced per zone pair, so moving a
          district changes what its customers pay.
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
                <details className="text-sm">
                  <summary className="cursor-pointer text-brand-700">Edit</summary>
                  <div className="mt-2">
                    <PricingForm action={updateZone} submitLabel="Save zone" className="space-y-2">
                      <input type="hidden" name="zoneId" value={zone.id ?? ""} />
                      <label className="block text-xs font-medium text-slate-700" htmlFor={`name-${zone.code}`}>
                        Name
                      </label>
                      <input
                        id={`name-${zone.code}`}
                        name="name"
                        className="input"
                        defaultValue={zone.name}
                        required
                        maxLength={128}
                      />
                      <label className="flex items-center gap-2 text-xs text-slate-700">
                        <input type="checkbox" name="active" defaultChecked={zone.active} />
                        Available for new quotes
                      </label>
                      <p className="text-xs text-slate-500">
                        A zone code never changes, because rate cards and districts are keyed on it. A zone cannot be
                        switched off while districts still point at it.
                      </p>
                    </PricingForm>
                  </div>
                </details>
              </li>
            ))}
          </ul>
        </section>

        <div className="space-y-6">
          <section className="card space-y-3">
            <h2 className="font-semibold">Move a district</h2>
            <PricingForm action={assignDistrict} submitLabel="Reassign district">
              <label className="block text-sm font-medium text-slate-700" htmlFor="district">
                District
              </label>
              <select id="district" name="district" className="input" required>
                <option value="">Select a district</option>
                {assignments.map((assignment) => (
                  <option key={assignment.district} value={assignment.district}>
                    {assignment.district} (now {assignment.zoneCode})
                  </option>
                ))}
              </select>

              <label className="block text-sm font-medium text-slate-700" htmlFor="zoneCode">
                New zone
              </label>
              <select id="zoneCode" name="zoneCode" className="input" required>
                <option value="">Select a zone</option>
                {activeZones.map((zone) => (
                  <option key={zone.code} value={zone.code}>
                    {zone.code} - {zone.name}
                  </option>
                ))}
              </select>
            </PricingForm>
          </section>

          <section className="card space-y-3">
            <h2 className="font-semibold">Add a zone</h2>
            <PricingForm action={createZone} submitLabel="Create zone">
              <label className="block text-sm font-medium text-slate-700" htmlFor="code">
                Code
              </label>
              <input
                id="code"
                name="code"
                className="input"
                placeholder="NWP"
                pattern="[A-Za-z][A-Za-z0-9-]{1,15}"
                title="2 to 16 letters, digits or hyphens"
                required
              />
              <label className="block text-sm font-medium text-slate-700" htmlFor="zoneName">
                Name
              </label>
              <input id="zoneName" name="name" className="input" placeholder="North Western" required maxLength={128} />
              <p className="text-xs text-slate-500">
                A new zone has no districts and no rates until you add them, so it cannot be quoted yet.
              </p>
            </PricingForm>
          </section>

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
                  <PricingForm action={updateSizeClass} submitLabel="Save" className="flex flex-col gap-2 sm:flex-row sm:items-end">
                    <input type="hidden" name="code" value={size.code} />
                    <span className="flex-1">
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
