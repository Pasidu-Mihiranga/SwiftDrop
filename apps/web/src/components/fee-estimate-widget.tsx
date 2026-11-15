"use client";

import dynamic from "next/dynamic";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { BffError, bffFetch, fieldErrors, problemMessage } from "@/lib/bff";
import type { MapConfig } from "@/lib/map-config";
import {
  EMPTY_QUOTE_FORM,
  MAX_SIDE_CM,
  MAX_WEIGHT_KG,
  SERVICE_LEVELS,
  SIZE_CLASSES,
  VEHICLE_TYPES,
  breakdownLabel,
  distanceBadge,
  pricingErrorMessage,
  transitDaysLabel,
  visibleBreakdown,
  districtOptions,
  formatMoney,
  distanceNotice,
  quoteLifetime,
  sizeClassLabel,
  serviceLevelLabel,
  sizeClassNotice,
  validateQuoteForm,
  weightExplanation,
  withDistrict,
  withPickedPoint,
  type GeoPick,
  type LocatedPoint,
  type Quote,
  type QuoteFieldErrors,
  type QuoteFormValues,
  type SpecialHandlingOption,
  type Zone,
} from "@/lib/pricing";

interface FeeEstimateWidgetProps {
  /** Coverage loaded on the server, so the browser never holds its own district list. */
  zones: Zone[];
  /** Handling options loaded on the server. Empty hides the checkboxes. */
  specialHandling?: SpecialHandlingOption[];
  /** Shown when the zones could not be loaded at all. */
  coverageError?: string;
  /** Called with the quote id so a booking flow can carry it forward. */
  onQuote?: (quote: Quote) => void;
  /** Tile settings for the optional map. Absent hides the map and leaves the district lists. */
  map?: MapConfig;
}

/** Leaflet needs a browser, so the map is loaded on the client only, and only when it is used. */
const LocationPicker = dynamic(() => import("./location-picker").then((module) => module.LocationPicker), {
  ssr: false,
  loading: () => <p className="text-xs text-slate-600">Loading map...</p>,
});

type Status = "idle" | "loading" | "quoted" | "failed";

export function FeeEstimateWidget({
  zones,
  specialHandling = [],
  coverageError,
  onQuote,
  map,
}: FeeEstimateWidgetProps) {
  const [values, setValues] = useState<QuoteFormValues>(EMPTY_QUOTE_FORM);
  const [errors, setErrors] = useState<QuoteFieldErrors>({});
  const [status, setStatus] = useState<Status>("idle");
  const [quote, setQuote] = useState<Quote | undefined>();
  const [message, setMessage] = useState<string>();
  const [now, setNow] = useState(() => new Date());

  const inFlight = useRef<AbortController>(undefined);
  const districts = useMemo(() => districtOptions(zones), [zones]);

  // One ticking clock for the countdown, and only while there is a quote to count down.
  useEffect(() => {
    if (!quote) return;
    const timer = setInterval(() => setNow(new Date()), 1000);
    return () => clearInterval(timer);
  }, [quote]);

  // Abandon an in-flight request if the widget goes away, so no stale result lands.
  useEffect(() => () => inFlight.current?.abort(), []);

  const lifetime = quote ? quoteLifetime(quote.expiresAt, now) : undefined;
  const expired = lifetime?.expired ?? false;

  const toggleHandling = useCallback((code: string, checked: boolean) => {
    setValues((current) => ({
      ...current,
      specialHandling: checked
        ? [...current.specialHandling, code]
        : current.specialHandling.filter((existing) => existing !== code),
    }));
    setErrors((current) => ({ ...current, specialHandling: undefined }));
  }, []);

  const chooseDistrict = useCallback((side: "from" | "to", district: string) => {
    // A pinned point is kept only while it is still in the district shown.
    setValues((current) => {
      const pinned = side === "from" ? current.fromPoint : current.toPoint;
      const pinnedDistrict = pinned ? (side === "from" ? current.fromDistrict : current.toDistrict) : undefined;
      return withDistrict(current, side, district, pinnedDistrict);
    });
    setErrors((current) => ({ ...current, [side === "from" ? "fromDistrict" : "toDistrict"]: undefined }));
  }, []);

  const pickPoint = useCallback((side: "from" | "to", point: GeoPick, located: LocatedPoint) => {
    setValues((current) => withPickedPoint(current, side, point, located));
    setErrors((current) => ({ ...current, [side === "from" ? "fromDistrict" : "toDistrict"]: undefined }));
  }, []);

  const update = useCallback((field: keyof QuoteFormValues, value: string) => {
    setValues((current) => ({ ...current, [field]: value }));
    setErrors((current) => ({ ...current, [field]: undefined }));
  }, []);

  async function submit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();

    const validation = validateQuoteForm(values, districts);
    if (!validation.ok) {
      setErrors(validation.errors);
      setStatus(quote ? "quoted" : "idle");
      return;
    }

    inFlight.current?.abort();
    const controller = new AbortController();
    inFlight.current = controller;

    setErrors({});
    setMessage(undefined);
    setStatus("loading");

    try {
      const issued = await bffFetch<Quote>(
        "api/v1/pricing/quotes",
        { method: "POST", body: validation.body, signal: controller.signal },
        fetch,
      );
      setQuote(issued);
      setNow(new Date());
      setStatus("quoted");
      onQuote?.(issued);
    } catch (error) {
      if (error instanceof DOMException && error.name === "AbortError") return;
      setErrors(fieldErrors(error));
      const code = error instanceof BffError ? error.problem.code : undefined;
      setMessage(pricingErrorMessage(code) ?? problemMessage(error));
      setQuote(undefined);
      setStatus("failed");
    }
  }

  const loading = status === "loading";

  return (
    <section className="space-y-4" aria-labelledby="fee-estimate-heading">
      <h2 id="fee-estimate-heading" className="text-lg font-semibold">
        Estimate your delivery fee
      </h2>

      {coverageError ? (
        <p role="alert" className="notice-warning">
          {coverageError}
        </p>
      ) : null}

      <form onSubmit={submit} className="space-y-4" noValidate>
        <div className="grid gap-4 sm:grid-cols-2">
          <div className="min-w-0 space-y-2">
            <DistrictField
              id="fromDistrict"
              label="Collect from"
              value={values.fromDistrict}
              districts={districts}
              error={errors.fromDistrict}
              onChange={(value) => chooseDistrict("from", value)}
            />
            {map ? (
              <MapToggle id="fromMap" purpose="collection">
                <LocationPicker
                  id="fromMap"
                  purpose="collection"
                  tileUrl={map.tileUrl}
                  attribution={map.attribution}
                  point={values.fromPoint}
                  onPick={(point, located) => pickPoint("from", point, located)}
                />
              </MapToggle>
            ) : null}
          </div>
          <div className="min-w-0 space-y-2">
            <DistrictField
              id="toDistrict"
              label="Deliver to"
              value={values.toDistrict}
              districts={districts}
              error={errors.toDistrict}
              onChange={(value) => chooseDistrict("to", value)}
            />
            {map ? (
              <MapToggle id="toMap" purpose="delivery">
                <LocationPicker
                  id="toMap"
                  purpose="delivery"
                  tileUrl={map.tileUrl}
                  attribution={map.attribution}
                  point={values.toPoint}
                  onPick={(point, located) => pickPoint("to", point, located)}
                />
              </MapToggle>
            ) : null}
          </div>

          <Field id="weightKg" label="Weight (kg)" error={errors.weightKg}>
            <input
              id="weightKg"
              name="weightKg"
              type="number"
              inputMode="decimal"
              min="0.01"
              max={MAX_WEIGHT_KG}
              step="0.01"
              className="input"
              value={values.weightKg}
              onChange={(event) => update("weightKg", event.target.value)}
              aria-invalid={Boolean(errors.weightKg)}
              aria-describedby={errors.weightKg ? "weightKg-error" : undefined}
            />
          </Field>

          <Field id="sizeClass" label="Parcel size" error={errors.sizeClass}>
            <select
              id="sizeClass"
              name="sizeClass"
              className="input"
              value={values.sizeClass}
              onChange={(event) => update("sizeClass", event.target.value)}
              aria-invalid={Boolean(errors.sizeClass)}
              aria-describedby={errors.sizeClass ? "sizeClass-error" : undefined}
            >
              {SIZE_CLASSES.map((code) => (
                <option key={code} value={code}>
                  {sizeClassLabel(code)} ({code})
                </option>
              ))}
            </select>
          </Field>

          <Field id="serviceLevel" label="Service" error={errors.serviceLevel}>
            <select
              id="serviceLevel"
              name="serviceLevel"
              className="input"
              value={values.serviceLevel}
              onChange={(event) => update("serviceLevel", event.target.value)}
              aria-invalid={Boolean(errors.serviceLevel)}
              aria-describedby={errors.serviceLevel ? "serviceLevel-error" : undefined}
            >
              {SERVICE_LEVELS.map((level) => (
                <option key={level} value={level}>
                  {serviceLevelLabel(level)}
                </option>
              ))}
            </select>
          </Field>

          {values.serviceLevel === "SAME_DAY" ? (
            <Field id="vehicleType" label="Vehicle" error={errors.vehicleType}>
              <select
                id="vehicleType"
                name="vehicleType"
                className="input"
                value={values.vehicleType}
                onChange={(event) => update("vehicleType", event.target.value)}
                aria-invalid={Boolean(errors.vehicleType)}
                aria-describedby={errors.vehicleType ? "vehicleType-error" : undefined}
              >
                {VEHICLE_TYPES.map((type) => (
                  <option key={type} value={type}>
                    {vehicleTypeLabel(type)}
                  </option>
                ))}
              </select>
            </Field>
          ) : null}

          <Field
            id="codAmount"
            label="Cash to collect (optional)"
            error={errors.codAmount}
            hint="Leave blank if the recipient pays nothing on delivery."
          >
            <input
              id="codAmount"
              name="codAmount"
              type="number"
              inputMode="decimal"
              min="0"
              step="0.01"
              className="input"
              value={values.codAmount}
              onChange={(event) => update("codAmount", event.target.value)}
              aria-invalid={Boolean(errors.codAmount)}
              aria-describedby={errors.codAmount ? "codAmount-error" : "codAmount-hint"}
            />
          </Field>
          <Field
            id="declaredValue"
            label="Declared value (optional)"
            error={errors.declaredValue}
            hint="Insure the parcel for what it is worth. Leave blank to send it without cover."
          >
            <input
              id="declaredValue"
              name="declaredValue"
              type="number"
              inputMode="decimal"
              min="0"
              step="0.01"
              className="input"
              value={values.declaredValue}
              onChange={(event) => update("declaredValue", event.target.value)}
              aria-invalid={Boolean(errors.declaredValue)}
              aria-describedby={errors.declaredValue ? "declaredValue-error" : "declaredValue-hint"}
            />
          </Field>
        </div>

        {specialHandling.length > 0 ? (
          <fieldset className="space-y-2">
            <legend className="text-sm font-medium text-slate-700">Special handling (optional)</legend>
            {specialHandling.map((option) => (
              <label key={option.code} className="flex items-center gap-2 text-sm text-slate-700">
                <input
                  type="checkbox"
                  name="specialHandling"
                  value={option.code}
                  checked={values.specialHandling.includes(option.code)}
                  onChange={(event) => toggleHandling(option.code, event.target.checked)}
                />
                {option.label}
              </label>
            ))}
            {errors.specialHandling ? <p className="field-error">{errors.specialHandling}</p> : null}
          </fieldset>
        ) : null}

        <fieldset className="space-y-2">
          <legend className="text-sm font-medium text-slate-700">Parcel measurements (optional)</legend>
          <p id="dimensions-hint" className="text-xs text-slate-600">
            A large, light parcel takes up room a heavy one does not, so measuring it gives a
            truer price. Give all three or leave them blank.
          </p>
          <div className="grid gap-4 sm:grid-cols-3">
            <SideField
              id="lengthCm"
              label="Length (cm)"
              value={values.lengthCm}
              error={errors.lengthCm}
              onChange={(value) => update("lengthCm", value)}
            />
            <SideField
              id="widthCm"
              label="Width (cm)"
              value={values.widthCm}
              error={errors.widthCm}
              onChange={(value) => update("widthCm", value)}
            />
            <SideField
              id="heightCm"
              label="Height (cm)"
              value={values.heightCm}
              error={errors.heightCm}
              onChange={(value) => update("heightCm", value)}
            />
          </div>
        </fieldset>

        <button className="btn-primary w-full sm:w-auto" type="submit" disabled={loading || districts.length === 0}>
          {loading ? "Calculating..." : quote ? "Recalculate" : "Get estimate"}
        </button>
      </form>

      <div aria-live="polite" aria-atomic="true">
        {loading ? <p className="text-sm text-slate-600">Calculating your fee...</p> : null}

        {status === "failed" && message ? (
          <p role="alert" className="notice-error">
            {message}
          </p>
        ) : null}

        {status === "idle" && !coverageError ? (
          <p className="notice-info">
            Pick two districts and a weight to see the price before you sign up.
          </p>
        ) : null}

        {quote && lifetime ? <QuoteSummary quote={quote} expired={expired} remaining={lifetime.label} /> : null}
      </div>
    </section>
  );
}

function QuoteSummary({ quote, expired, remaining }: { quote: Quote; expired: boolean; remaining: string }) {
  const billedWeight = weightExplanation(quote);
  const reclassified = sizeClassNotice(quote);
  const estimatedRoute = distanceNotice(quote);
  const badge = distanceBadge(quote);
  const transit = transitDaysLabel(quote.estimatedTransitDays);
  const chargeable = quote.chargeableWeightKg ?? quote.weightKg;
  return (
    <div className="min-w-0 space-y-4 border-t border-slate-200 pt-4">
      <div className="flex flex-wrap items-baseline justify-between gap-2">
        <div>
          <p className="text-sm text-slate-600">Estimated fee</p>
          <p className="break-words text-3xl font-bold">{formatMoney(quote.fee.amount, quote.fee.currency)}</p>
        </div>
        <span
          className={
            expired
              ? "status-pill-warning px-3 py-1"
              : "status-pill-neutral px-3 py-1"
          }
        >
          {expired ? "Estimate expired" : `Valid for ${remaining}`}
        </span>
      </div>

      <p className="text-sm text-slate-600">
        Zone {quote.fromZone} to zone {quote.toZone}
      </p>

      <dl className="grid gap-x-4 gap-y-1 text-sm sm:grid-cols-2">
        {transit ? (
          <div>
            <dt className="text-slate-600">Estimated transit</dt>
            <dd>{transit}</dd>
          </div>
        ) : null}
        {quote.distanceKm !== undefined ? (
          <div>
            <dt className="text-slate-600">Distance</dt>
            <dd>
              {quote.distanceKm} km
              {badge ? <span className="status-pill-warning ml-2">{badge}</span> : null}
            </dd>
          </div>
        ) : null}
        <div>
          <dt className="text-slate-600">Actual weight</dt>
          <dd>{quote.weightKg} kg</dd>
        </div>
        <div>
          <dt className="text-slate-600">Chargeable weight</dt>
          <dd>{chargeable} kg</dd>
        </div>
        {quote.vehicleType ? (
          <div>
            <dt className="text-slate-600">Vehicle</dt>
            <dd>{vehicleTypeLabel(quote.vehicleType)}</dd>
          </div>
        ) : null}
      </dl>

      {billedWeight ? <p className="notice-info text-sm">{billedWeight}</p> : null}
      {reclassified ? <p className="notice-info text-sm">{reclassified}</p> : null}
      {estimatedRoute ? <p className="notice-warning text-sm">{estimatedRoute}</p> : null}

      <ul className="divide-y divide-slate-100 text-sm">
        {visibleBreakdown(quote.breakdown).map((line) => (
          <li key={line.rule} className="flex items-center justify-between py-2">
            <span>{breakdownLabel(line.rule)}</span>
            <span className="tabular-nums">{formatMoney(line.amount, quote.fee.currency)}</span>
          </li>
        ))}
        <li className="flex items-center justify-between py-2 font-semibold">
          <span>Total</span>
          <span className="tabular-nums">{formatMoney(quote.fee.amount, quote.fee.currency)}</span>
        </li>
      </ul>

      {expired ? (
        <p role="alert" className="notice-warning">
          Prices can change, so this estimate only lasts a few minutes. Recalculate to continue.
        </p>
      ) : (
        <p className="break-all text-xs text-slate-500">Reference {quote.quoteId}</p>
      )}
      {quote.pricingVersion ? (
        <p className="text-xs text-slate-500">Pricing version {quote.pricingVersion}</p>
      ) : null}
    </div>
  );
}

function vehicleTypeLabel(type: string): string {
  return type === "VAN" ? "Van" : type === "BIKE" ? "Bike" : type;
}

function SideField({
  id,
  label,
  value,
  error,
  onChange,
}: {
  id: string;
  label: string;
  value: string;
  error?: string;
  onChange: (value: string) => void;
}) {
  return (
    <Field id={id} label={label} error={error}>
      <input
        id={id}
        name={id}
        type="number"
        inputMode="decimal"
        min="0.1"
        max={MAX_SIDE_CM}
        step="0.1"
        className="input"
        value={value}
        onChange={(event) => onChange(event.target.value)}
        aria-invalid={Boolean(error)}
        aria-describedby={error ? `${id}-error` : "dimensions-hint"}
      />
    </Field>
  );
}

/**
 * Keeps the map unmounted until asked for. A closed details element still renders its children,
 * so the picker is mounted only once opened: nobody downloads Leaflet or tiles they did not want.
 */
function MapToggle({ id, purpose, children }: { id: string; purpose: string; children: React.ReactNode }) {
  const [open, setOpen] = useState(false);
  return (
    <details
      className="text-sm"
      id={`${id}-toggle`}
      onToggle={(event) => setOpen((event.currentTarget as HTMLDetailsElement).open)}
    >
      <summary className="cursor-pointer text-brand-700">Pin the exact {purpose} point on a map</summary>
      {open ? <div className="mt-2">{children}</div> : null}
    </details>
  );
}

function DistrictField({
  id,
  label,
  value,
  districts,
  error,
  onChange,
}: {
  id: string;
  label: string;
  value: string;
  districts: string[];
  error?: string;
  onChange: (value: string) => void;
}) {
  const empty = districts.length === 0;
  return (
    <Field id={id} label={label} error={error}>
      <select
        id={id}
        name={id}
        className="input"
        value={value}
        disabled={empty}
        onChange={(event) => onChange(event.target.value)}
        aria-invalid={Boolean(error)}
        aria-describedby={error ? `${id}-error` : undefined}
      >
        <option value="">{empty ? "Coverage unavailable" : "Select a district"}</option>
        {districts.map((district) => (
          <option key={district} value={district}>
            {district}
          </option>
        ))}
      </select>
    </Field>
  );
}

function Field({
  id,
  label,
  error,
  hint,
  children,
}: {
  id: string;
  label: string;
  error?: string;
  hint?: string;
  children: React.ReactNode;
}) {
  return (
    <div className="space-y-1">
      <label htmlFor={id} className="block text-sm font-medium text-slate-700">
        {label}
      </label>
      {children}
      {hint && !error ? (
        <p id={`${id}-hint`} className="text-xs text-slate-500">
          {hint}
        </p>
      ) : null}
      {error ? (
        <p id={`${id}-error`} className="field-error">
          {error}
        </p>
      ) : null}
    </div>
  );
}
