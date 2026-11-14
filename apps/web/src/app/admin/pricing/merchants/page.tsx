import Link from "next/link";
import { PricingForm } from "@/components/pricing-form";
import { publishMerchantAgreement } from "@/lib/pricing-actions";
import { fetchAdminZones, fetchAgreementsInForce, fetchMerchantAgreements } from "@/lib/pricing-api";
import {
  describeAgreement,
  formatEffectivePeriod,
  formatMoney,
  formatPercent,
  isoDate,
  isUuid,
  laneLabel,
  periodStatus,
  type MerchantAgreement,
} from "@/lib/pricing";

export const metadata = { title: "Merchant rates" };

/**
 * Negotiated terms. These sit over the published rate cards rather than replacing them, so a
 * change to a published rate still reaches every merchant. Merchant identities belong to
 * merchant-service, so this screen starts from who holds terms today, or from a known id.
 */
export default async function MerchantRatesAdminPage({
  searchParams,
}: {
  searchParams: Promise<{ merchantId?: string }>;
}) {
  const { merchantId = "" } = await searchParams;
  const selected = merchantId.trim();
  const valid = isUuid(selected);

  const [inForce, zones, history] = await Promise.all([
    fetchAgreementsInForce(),
    fetchAdminZones(),
    valid ? fetchMerchantAgreements(selected) : Promise.resolve<MerchantAgreement[]>([]),
  ]);
  const zoneOptions = zones.filter((zone) => zone.active);
  const today = isoDate();

  return (
    <div className="space-y-6">
      <div className="space-y-1">
        <h1 className="text-2xl font-bold">Merchant rates</h1>
        <p className="text-slate-600">
          {inForce.length} {inForce.length === 1 ? "merchant has" : "merchants have"} negotiated terms today. Everyone else
          pays the published rates.
        </p>
      </div>

      <section className="card space-y-3">
        <h2 className="font-semibold">Terms in force</h2>
        {inForce.length === 0 ? (
          <p className="notice-info">No merchant has negotiated terms yet.</p>
        ) : (
          <ul className="divide-y divide-slate-100 text-sm">
            {inForce.map((agreement) => (
              <li key={agreement.id} className="space-y-1 py-3">
                <Link
                  className="break-all font-medium text-brand-700"
                  href={`/admin/pricing/merchants?merchantId=${agreement.merchantId}`}
                >
                  {agreement.merchantId}
                </Link>
                <p className="text-xs text-slate-600">{describeAgreement(agreement)}</p>
              </li>
            ))}
          </ul>
        )}
      </section>

      <section className="card space-y-3">
        <h2 className="font-semibold">Find a merchant</h2>
        <form className="flex flex-col gap-2 sm:flex-row sm:items-end" method="get">
          <span className="flex-1">
            <label className="block text-sm font-medium text-slate-700" htmlFor="findMerchantId">
              Merchant id
            </label>
            <input id="findMerchantId" name="merchantId" className="input" defaultValue={selected} />
          </span>
          <button className="btn-primary" type="submit">
            Show history
          </button>
        </form>
        {selected && !valid ? <p className="notice-error">That is not a valid merchant id.</p> : null}
        {valid && history.length === 0 ? <p className="notice-info">This merchant has never had negotiated terms.</p> : null}
        {history.length > 0 ? (
          <ul className="divide-y divide-slate-100 text-sm">
            {history.map((agreement) => (
              <AgreementRow key={agreement.id} agreement={agreement} today={today} />
            ))}
          </ul>
        ) : null}
      </section>

      <section className="card space-y-3">
        <h2 className="font-semibold">Publish terms</h2>
        <PricingForm action={publishMerchantAgreement} submitLabel="Publish terms">
          <div className="grid gap-3 sm:grid-cols-2">
            <span className="sm:col-span-2">
              <label className="block text-sm font-medium text-slate-700" htmlFor="merchantId">
                Merchant id
              </label>
              <input id="merchantId" name="merchantId" className="input" defaultValue={valid ? selected : ""} required />
            </span>
            <span>
              <label className="block text-sm font-medium text-slate-700" htmlFor="discountPct">
                Discount fraction
              </label>
              <input id="discountPct" name="discountPct" type="number" min="0" max="1" step="0.0001" className="input" />
              <span className="text-xs text-slate-500">A fraction, not a percentage: 0.1 is 10 per cent.</span>
            </span>
            <span>
              <label className="block text-sm font-medium text-slate-700" htmlFor="discountFlat">
                Off each parcel (LKR)
              </label>
              <input id="discountFlat" name="discountFlat" type="number" min="0" step="0.01" className="input" />
            </span>
            <span>
              <label className="block text-sm font-medium text-slate-700" htmlFor="effectiveFrom">
                Takes effect on
              </label>
              <input id="effectiveFrom" name="effectiveFrom" type="date" className="input" required />
            </span>
          </div>

          <fieldset className="space-y-1">
            <legend className="text-sm font-medium text-slate-700">Charges waived</legend>
            {([
              ["waivesRemoteArea", "Remote-area charge"],
              ["waivesInsurance", "Cover charge"],
              ["waivesCodFee", "Cash-on-delivery fee"],
            ] as const).map(([field, label]) => (
              <label key={field} className="flex items-center gap-2 text-sm text-slate-700">
                <input type="checkbox" name={field} />
                {label}
              </label>
            ))}
          </fieldset>

          <fieldset className="space-y-2">
            <legend className="text-sm font-medium text-slate-700">Negotiated lanes (optional)</legend>
            <p className="text-xs text-slate-500">
              A lane&apos;s discount replaces the one above for that lane. Its base fee replaces the rate card&apos;s.
            </p>
            {[1, 2, 3].map((row) => (
              <div key={row} className="grid grid-cols-2 gap-2 sm:grid-cols-4">
                <span>
                  <label className="block text-xs font-medium text-slate-700" htmlFor={`laneFrom${row}`}>
                    From
                  </label>
                  <select id={`laneFrom${row}`} name={`laneFrom${row}`} className="input">
                    <option value="">Select</option>
                    {zoneOptions.map((zone) => (
                      <option key={zone.code} value={zone.code}>
                        {zone.code}
                      </option>
                    ))}
                  </select>
                </span>
                <span>
                  <label className="block text-xs font-medium text-slate-700" htmlFor={`laneTo${row}`}>
                    To
                  </label>
                  <select id={`laneTo${row}`} name={`laneTo${row}`} className="input">
                    <option value="">Select</option>
                    {zoneOptions.map((zone) => (
                      <option key={zone.code} value={zone.code}>
                        {zone.code}
                      </option>
                    ))}
                  </select>
                </span>
                <span>
                  <label className="block text-xs font-medium text-slate-700" htmlFor={`laneDiscount${row}`}>
                    Discount fraction
                  </label>
                  <input id={`laneDiscount${row}`} name={`laneDiscount${row}`} type="number" min="0" max="1" step="0.0001" className="input" />
                </span>
                <span>
                  <label className="block text-xs font-medium text-slate-700" htmlFor={`laneBaseFee${row}`}>
                    Base fee (LKR)
                  </label>
                  <input id={`laneBaseFee${row}`} name={`laneBaseFee${row}`} type="number" min="0" step="0.01" className="input" />
                </span>
              </div>
            ))}
          </fieldset>

          <p className="text-xs text-slate-500">
            New terms replace the current ones from that date, including any lanes not listed again. To fix a mistake,
            publish again from a later date: periods cannot be rewritten.
          </p>
        </PricingForm>
      </section>
    </div>
  );
}

function AgreementRow({ agreement, today }: { agreement: MerchantAgreement; today: string }) {
  const status = periodStatus(agreement, today);
  return (
    <li className="space-y-1 py-3">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <span className="font-medium">{describeAgreement(agreement)}</span>
        <span className={status === "In force" ? "status-pill-success" : "status-pill-neutral"}>{status}</span>
      </div>
      <p className="text-xs text-slate-500">{formatEffectivePeriod(agreement.effectiveFrom, agreement.effectiveTo)}</p>
      {agreement.laneOverrides.length > 0 ? (
        <ul className="text-xs text-slate-600">
          {agreement.laneOverrides.map((lane) => (
            <li key={`${lane.fromZone}-${lane.toZone}`}>
              {laneLabel(lane.fromZone, lane.toZone)}
              {lane.discountPct != null ? `: ${formatPercent(lane.discountPct)} off` : ""}
              {lane.flatBaseFee != null ? `: base ${formatMoney(lane.flatBaseFee)}` : ""}
            </li>
          ))}
        </ul>
      ) : null}
    </li>
  );
}
