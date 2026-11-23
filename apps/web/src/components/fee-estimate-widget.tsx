"use client";

import dynamic from "next/dynamic";
import Link from "next/link";
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
  quoteToBookingHref,
  sizeClassLabel,
  sizeClassNotice,
  serviceLevelLabel,
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

/** Leaflet needs a browser, so the picker is loaded on the client only, and only when it is used. */
const LocationPicker = dynamic(() => import("./location-picker").then((module) => module.LocationPicker), {
  ssr: false,
  loading: () => <p className="text-xs text-slate-600">Loading map...</p>,
});

type Status = "idle" | "loading" | "quoted" | "failed";

const PARCEL_SIZE_OPTIONS: { code: (typeof SIZE_CLASSES)[number]; label: string }[] = [
  { code: "S", label: "Small (up to 30 cm)" },
  { code: "M", label: "Medium (up to 50 cm)" },
  { code: "L", label: "Large (up to 80 cm)" },
  { code: "XL", label: "Extra Large (up to 120 cm)" },
];

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
  const [showMapSection, setShowMapSection] = useState(false);
  const [isMapMaximized, setIsMapMaximized] = useState(false);
  const [activeMapSide, setActiveMapSide] = useState<"from" | "to">("from");
  const [openSections, setOpenSections] = useState({
    step1: true,
    step2: true,
    step3: true,
    measurements: false,
  });

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

  const update = useCallback((field: keyof QuoteFormValues, value: string | number) => {
    setValues((current) => ({ ...current, [field]: value }));
    setErrors((current) => ({ ...current, [field]: undefined }));
  }, []);

  const swapLocations = useCallback(() => {
    setValues((current) => ({
      ...current,
      fromAddress: current.toAddress,
      fromDistrict: current.toDistrict,
      fromPoint: current.toPoint,
      toAddress: current.fromAddress,
      toDistrict: current.fromDistrict,
      toPoint: current.fromPoint,
    }));
    setErrors((current) => ({
      ...current,
      fromDistrict: undefined,
      toDistrict: undefined,
    }));
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
    <div className="space-y-6">
      {/* Top Header Card matching reference screenshot */}
      <div className="flex items-start gap-4">
        <div className="flex h-12 w-12 shrink-0 items-center justify-center rounded-2xl bg-brand-50 text-brand-600 ring-1 ring-brand-100">
          <svg className="h-6 w-6" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path
              strokeLinecap="round"
              strokeLinejoin="round"
              strokeWidth={1.75}
              d="M9 17a2 2 0 11-4 0 2 2 0 014 0zM19 17a2 2 0 11-4 0 2 2 0 014 0z"
            />
            <path
              strokeLinecap="round"
              strokeLinejoin="round"
              strokeWidth={1.75}
              d="M13 16V6a1 1 0 00-1-1H4a1 1 0 00-1 1v10a1 1 0 001 1h1m8-1a1 1 0 01-1 1H9m4-1V8a1 1 0 011-1h2.586a1 1 0 01.707.293l3.414 3.414a1 1 0 01.293.707V16a1 1 0 01-1 1h-1m-6-1a1 1 0 001 1h1M5 17a2 2 0 104 0m-4 0a2 2 0 114 0m6 0a2 2 0 104 0m-4 0a2 2 0 114 0"
            />
          </svg>
        </div>
        <div>
          <h1 id="fee-estimate-heading" className="text-2xl font-bold tracking-tight text-slate-900 sm:text-3xl">
            Calculate Delivery Price
          </h1>
          <p className="mt-1 text-sm text-slate-500">
            Get an instant delivery cost estimate across Sri Lanka.
          </p>
        </div>
      </div>

      {coverageError ? (
        <div role="alert" className="notice-warning flex items-center justify-between gap-2">
          <span>{coverageError}</span>
          <button
            type="button"
            onClick={() => window.location.reload()}
            className="text-xs font-semibold underline hover:text-slate-900"
          >
            Retry
          </button>
        </div>
      ) : null}

      <div className="grid min-w-0 gap-6 lg:grid-cols-[minmax(0,1.8fr)_minmax(0,1.2fr)] items-start">
        {/* Left Form: Step 1, Step 2, Step 3 */}
        <form onSubmit={submit} className="space-y-5" noValidate>
          {/* Step 1: Pickup & Delivery */}
          <section className="rounded-2xl bg-white p-5 shadow-xs ring-1 ring-slate-200/80 sm:p-6 space-y-4">
            <div className="flex items-center justify-between border-b border-slate-100 pb-3">
              <button
                type="button"
                onClick={() => setOpenSections((s) => ({ ...s, step1: !s.step1 }))}
                className="flex items-center gap-2.5 text-left focus:outline-none"
                aria-expanded={openSections.step1}
              >
                <span className="flex h-6 w-6 items-center justify-center rounded-full bg-brand-600 text-xs font-bold text-white shadow-xs">
                  1
                </span>
                <h2 className="text-base font-semibold text-slate-900">Pickup &amp; Delivery</h2>
                <svg
                  className={`h-4 w-4 text-slate-400 transition-transform duration-200 ${
                    openSections.step1 ? "rotate-180" : ""
                  }`}
                  fill="none"
                  viewBox="0 0 24 24"
                  stroke="currentColor"
                >
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 9l-7 7-7-7" />
                </svg>
              </button>
              {map ? (
                <button
                  type="button"
                  onClick={() => {
                    setShowMapSection((prev) => !prev);
                    if (!openSections.step1) setOpenSections((s) => ({ ...s, step1: true }));
                  }}
                  className="inline-flex items-center gap-1.5 rounded-lg border border-slate-200 bg-slate-50/80 px-2.5 py-1 text-xs font-medium text-slate-700 hover:bg-slate-100 transition"
                >
                  <svg className="h-3.5 w-3.5 text-brand-600" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                    <path
                      strokeLinecap="round"
                      strokeLinejoin="round"
                      strokeWidth={2}
                      d="M17.657 16.657L13.414 20.9a1.998 1.998 0 01-2.827 0l-4.244-4.243a8 8 0 1111.314 0z"
                    />
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 11a3 3 0 11-6 0 3 3 0 016 0z" />
                  </svg>
                  <span>{showMapSection ? "Hide map" : "Pick on map"}</span>
                </button>
              ) : null}
            </div>

            {openSections.step1 ? (
              <div className="space-y-4">
                {/* Unified Map Selector Panel */}
                {showMapSection && map ? (
                  isMapMaximized ? (
                    <div
                      className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/60 p-3 sm:p-6 backdrop-blur-xs animate-in fade-in duration-200"
                      role="dialog"
                      aria-modal="true"
                      aria-labelledby="maximized-map-title"
                    >
                      <div className="flex h-[92vh] w-full max-w-5xl flex-col rounded-2xl bg-white p-4 sm:p-6 shadow-2xl ring-1 ring-slate-900/10">
                        <div className="flex flex-wrap items-center justify-between gap-3 border-b border-slate-200 pb-3">
                          <div className="flex items-center gap-2">
                            <span className="flex h-7 w-7 items-center justify-center rounded-lg bg-brand-50 text-brand-700">
                              <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M17.657 16.657L13.414 20.9a1.998 1.998 0 01-2.827 0l-4.244-4.243a8 8 0 1111.314 0z" />
                                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 11a3 3 0 11-6 0 3 3 0 016 0z" />
                              </svg>
                            </span>
                            <div>
                              <h3 id="maximized-map-title" className="text-sm font-bold text-slate-900">
                                Select location on map
                              </h3>
                              <p className="text-xs text-slate-500">
                                Tap anywhere in Sri Lanka to auto-detect the district.
                              </p>
                            </div>
                          </div>

                          <div className="flex items-center gap-2">
                            <button
                              type="button"
                              onClick={() => setIsMapMaximized(false)}
                              className="inline-flex items-center gap-1.5 rounded-lg border border-slate-200 bg-white px-3 py-1.5 text-xs font-semibold text-slate-700 shadow-xs hover:bg-slate-50 transition"
                              aria-label="Minimize"
                            >
                              <svg className="h-3.5 w-3.5 text-slate-500" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 14h6m0 0v6m0-6L3 21m17-7h-6m0 0v6m0-6l7 7M4 10h6m0 0V4m0 6L3 3m17 7h-6m0 0V4m0 6l7-7" />
                              </svg>
                              <span>Minimize</span>
                            </button>
                            <button
                              type="button"
                              onClick={() => {
                                setIsMapMaximized(false);
                                setShowMapSection(false);
                              }}
                              className="inline-flex items-center gap-1 rounded-lg bg-brand-600 px-3 py-1.5 text-xs font-semibold text-white shadow-xs hover:bg-brand-700 transition"
                            >
                              Done
                            </button>
                          </div>
                        </div>

                        {/* Tabs for Pickup / Delivery */}
                        <div className="flex items-center gap-2 pt-3">
                          <button
                            type="button"
                            onClick={() => setActiveMapSide("from")}
                            className={`flex items-center gap-1.5 rounded-lg px-3 py-1.5 text-xs font-semibold transition ${
                              activeMapSide === "from"
                                ? "bg-brand-600 text-white shadow-xs"
                                : "border border-slate-200 bg-white text-slate-700 hover:bg-slate-100"
                            }`}
                          >
                            <span className="h-2 w-2 rounded-full bg-blue-300" />
                            <span>Pickup (From)</span>
                            {values.fromPoint ? <span className="text-[10px]">&#10003;</span> : null}
                          </button>
                          <button
                            type="button"
                            onClick={() => setActiveMapSide("to")}
                            className={`flex items-center gap-1.5 rounded-lg px-3 py-1.5 text-xs font-semibold transition ${
                              activeMapSide === "to"
                                ? "bg-brand-600 text-white shadow-xs"
                                : "border border-slate-200 bg-white text-slate-700 hover:bg-slate-100"
                            }`}
                          >
                            <span className="h-2 w-2 rounded-full bg-red-400" />
                            <span>Delivery (To)</span>
                            {values.toPoint ? <span className="text-[10px]">&#10003;</span> : null}
                          </button>
                          <span className="ml-auto text-xs text-slate-500 hidden sm:inline">
                            {activeMapSide === "from"
                              ? values.fromDistrict ? `District: ${values.fromDistrict}` : "Click map to set pickup"
                              : values.toDistrict ? `District: ${values.toDistrict}` : "Click map to set delivery"}
                          </span>
                        </div>

                        <div className="mt-3 flex-1 overflow-hidden">
                          <LocationPicker
                            id={activeMapSide === "from" ? "fromMapMax" : "toMapMax"}
                            purpose={activeMapSide === "from" ? "collection" : "delivery"}
                            tileUrl={map.tileUrl}
                            attribution={map.attribution}
                            search={map.addressSearch}
                            point={activeMapSide === "from" ? values.fromPoint : values.toPoint}
                            onPick={(point, located) => pickPoint(activeMapSide, point, located)}
                            heightClass="h-full min-h-[50vh]"
                          />
                        </div>
                      </div>
                    </div>
                  ) : (
                    <div className="rounded-xl border border-slate-200 bg-slate-50/70 p-4 space-y-3">
                      <div className="flex flex-wrap items-center justify-between gap-2 border-b border-slate-200 pb-3">
                        <div className="flex items-center gap-2">
                          <button
                            type="button"
                            onClick={() => setActiveMapSide("from")}
                            className={`flex items-center gap-1.5 rounded-lg px-3 py-1.5 text-xs font-semibold transition ${
                              activeMapSide === "from"
                                ? "bg-brand-600 text-white shadow-xs"
                                : "border border-slate-200 bg-white text-slate-700 hover:bg-slate-100"
                            }`}
                          >
                            <span className="h-2 w-2 rounded-full bg-blue-300" />
                            <span>Pickup (From)</span>
                            {values.fromPoint ? <span className="text-[10px]">&#10003;</span> : null}
                          </button>
                          <button
                            type="button"
                            onClick={() => setActiveMapSide("to")}
                            className={`flex items-center gap-1.5 rounded-lg px-3 py-1.5 text-xs font-semibold transition ${
                              activeMapSide === "to"
                                ? "bg-brand-600 text-white shadow-xs"
                                : "border border-slate-200 bg-white text-slate-700 hover:bg-slate-100"
                            }`}
                          >
                            <span className="h-2 w-2 rounded-full bg-red-400" />
                            <span>Delivery (To)</span>
                            {values.toPoint ? <span className="text-[10px]">&#10003;</span> : null}
                          </button>
                        </div>
                        <div className="flex items-center gap-2">
                          <button
                            type="button"
                            onClick={() => setIsMapMaximized(true)}
                            className="inline-flex items-center gap-1.5 rounded-lg border border-slate-200 bg-white px-2.5 py-1 text-xs font-medium text-slate-700 hover:bg-slate-100 transition shadow-2xs"
                            title="Maximize map view"
                            aria-label="Maximize"
                          >
                            <svg className="h-3.5 w-3.5 text-slate-500" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 8V4m0 0h4M4 4l5 5m11-5h-4m4 0v4m0-4l-5 5M4 16v4m0 0h4m-4 0l5-5m11 5l-5-5m5 5v-4m0 4h-4" />
                            </svg>
                            <span>Maximize</span>
                          </button>
                          <button
                            type="button"
                            onClick={() => setShowMapSection(false)}
                            className="text-xs text-slate-500 hover:text-slate-800"
                          >
                            Close map
                          </button>
                        </div>
                      </div>
                      <p className="text-xs text-slate-600">
                        {activeMapSide === "from"
                          ? "Tap the map to set your pickup location. The district is filled in automatically."
                          : "Tap the map to set your delivery location. The district is filled in automatically."}
                      </p>
                      <LocationPicker
                        id={activeMapSide === "from" ? "fromMapUnified" : "toMapUnified"}
                        purpose={activeMapSide === "from" ? "collection" : "delivery"}
                        tileUrl={map.tileUrl}
                        attribution={map.attribution}
                        search={map.addressSearch}
                        point={activeMapSide === "from" ? values.fromPoint : values.toPoint}
                        onPick={(point, located) => pickPoint(activeMapSide, point, located)}
                      />
                    </div>
                  )
                ) : null}

                {/* Address & District Grid with Center Swap Button */}
                <div className="grid gap-4 sm:grid-cols-[minmax(0,1fr)_auto_minmax(0,1fr)] items-start">
                  {/* Pickup Side */}
                  <div className="space-y-2">
                    <div>
                      <label htmlFor="fromAddress" className="flex items-center gap-1.5 text-xs font-semibold text-slate-700">
                        <span className="h-2 w-2 rounded-full bg-brand-600" />
                        Pickup address (From)
                      </label>
                      <div className="relative mt-1">
                        <input
                          id="fromAddress"
                          type="text"
                          placeholder="45 Galle Road, Colombo 03"
                          value={values.fromAddress}
                          onChange={(e) => update("fromAddress", e.target.value)}
                          className="input pr-8 text-sm placeholder:text-slate-400"
                        />
                        {values.fromAddress ? (
                          <button
                            type="button"
                            onClick={() => update("fromAddress", "")}
                            aria-label="Clear pickup address"
                            className="absolute right-2.5 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-600"
                          >
                            &times;
                          </button>
                        ) : null}
                      </div>
                    </div>

                    <DistrictField
                      id="fromDistrict"
                      label="Collect from"
                      value={values.fromDistrict}
                      districts={districts}
                      error={errors.fromDistrict}
                      onChange={(value) => chooseDistrict("from", value)}
                    />
                  </div>

                  {/* Swap Button in Center */}
                  <div className="flex sm:flex-col items-center justify-center pt-6 sm:pt-7">
                    <button
                      type="button"
                      onClick={swapLocations}
                      title="Swap pickup and delivery locations"
                      className="flex h-9 w-9 items-center justify-center rounded-full border border-slate-200 bg-white text-slate-600 shadow-xs hover:border-brand-300 hover:bg-brand-50 hover:text-brand-700 transition"
                    >
                      <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                        <path
                          strokeLinecap="round"
                          strokeLinejoin="round"
                          strokeWidth={2}
                          d="M8 7h12m0 0l-4-4m4 4l-4 4m0 6H4m0 0l4 4m-4-4l4-4"
                        />
                      </svg>
                    </button>
                  </div>

                  {/* Delivery Side */}
                  <div className="space-y-2">
                    <div>
                      <label htmlFor="toAddress" className="flex items-center gap-1.5 text-xs font-semibold text-slate-700">
                        <span className="h-2 w-2 rounded-full bg-red-500" />
                        Delivery address (To)
                      </label>
                      <div className="relative mt-1">
                        <input
                          id="toAddress"
                          type="text"
                          placeholder="Peradeniya Road, Kandy"
                          value={values.toAddress}
                          onChange={(e) => update("toAddress", e.target.value)}
                          className="input pr-8 text-sm placeholder:text-slate-400"
                        />
                        {values.toAddress ? (
                          <button
                            type="button"
                            onClick={() => update("toAddress", "")}
                            aria-label="Clear delivery address"
                            className="absolute right-2.5 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-600"
                          >
                            &times;
                          </button>
                        ) : null}
                      </div>
                    </div>

                    <DistrictField
                      id="toDistrict"
                      label="Deliver to"
                      value={values.toDistrict}
                      districts={districts}
                      error={errors.toDistrict}
                      onChange={(value) => chooseDistrict("to", value)}
                    />
                  </div>
                </div>
              </div>
            ) : null}
          </section>

          {/* Step 2: Parcel Details */}
          <section className="rounded-2xl bg-white p-5 shadow-xs ring-1 ring-slate-200/80 sm:p-6 space-y-4">
            <div className="flex items-center justify-between border-b border-slate-100 pb-3">
              <button
                type="button"
                onClick={() => setOpenSections((s) => ({ ...s, step2: !s.step2 }))}
                className="flex items-center gap-2.5 text-left focus:outline-none"
                aria-expanded={openSections.step2}
              >
                <span className="flex h-6 w-6 items-center justify-center rounded-full bg-brand-600 text-xs font-bold text-white shadow-xs">
                  2
                </span>
                <h2 className="text-base font-semibold text-slate-900">Parcel Details</h2>
                <svg
                  className={`h-4 w-4 text-slate-400 transition-transform duration-200 ${
                    openSections.step2 ? "rotate-180" : ""
                  }`}
                  fill="none"
                  viewBox="0 0 24 24"
                  stroke="currentColor"
                >
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 9l-7 7-7-7" />
                </svg>
              </button>
            </div>

            {openSections.step2 ? (
              <div className="grid gap-4 sm:grid-cols-3">
                {/* Weight */}
                <Field id="weightKg" label="Weight (kg)" error={errors.weightKg}>
                  <input
                    id="weightKg"
                    name="weightKg"
                    type="number"
                    inputMode="decimal"
                    min="0.01"
                    max={MAX_WEIGHT_KG}
                    step="0.01"
                    className="input text-sm font-semibold text-slate-900"
                    value={values.weightKg}
                    onChange={(event) => update("weightKg", event.target.value)}
                    placeholder="e.g. 2.0"
                    aria-invalid={Boolean(errors.weightKg)}
                    aria-describedby={errors.weightKg ? "weightKg-error" : undefined}
                  />
                </Field>

                {/* Parcel Size */}
                <Field id="sizeClass" label="Parcel size" error={errors.sizeClass}>
                  <select
                    id="sizeClass"
                    name="sizeClass"
                    className="input text-sm"
                    value={values.sizeClass}
                    onChange={(event) => update("sizeClass", event.target.value)}
                    aria-invalid={Boolean(errors.sizeClass)}
                    aria-describedby={errors.sizeClass ? "sizeClass-error" : undefined}
                  >
                    {PARCEL_SIZE_OPTIONS.map((opt) => (
                      <option key={opt.code} value={opt.code}>
                        {opt.label}
                      </option>
                    ))}
                  </select>
                </Field>

                {/* Number of parcels stepper */}
                <div>
                  <label className="block text-sm font-medium text-slate-700">
                    Number of parcels
                  </label>
                  <div className="mt-1 flex h-10 items-center justify-between rounded-xl border border-slate-200 bg-white px-2 shadow-xs">
                    <button
                      type="button"
                      onClick={() => update("numberOfParcels", Math.max(1, values.numberOfParcels - 1))}
                      disabled={values.numberOfParcels <= 1}
                      className="flex h-7 w-7 items-center justify-center rounded-lg text-slate-600 hover:bg-slate-100 disabled:opacity-30 disabled:hover:bg-transparent"
                      aria-label="Decrease parcel quantity"
                    >
                      &minus;
                    </button>
                    <span className="font-semibold tabular-nums text-slate-900">{values.numberOfParcels}</span>
                    <button
                      type="button"
                      onClick={() => update("numberOfParcels", Math.min(20, values.numberOfParcels + 1))}
                      disabled={values.numberOfParcels >= 20}
                      className="flex h-7 w-7 items-center justify-center rounded-lg text-slate-600 hover:bg-slate-100 disabled:opacity-30 disabled:hover:bg-transparent"
                      aria-label="Increase parcel quantity"
                    >
                      +
                    </button>
                  </div>
                </div>
              </div>
            ) : null}
          </section>

          {/* Step 3: Service & Options */}
          <section className="rounded-2xl bg-white p-5 shadow-xs ring-1 ring-slate-200/80 sm:p-6 space-y-4">
            <div className="flex items-center justify-between border-b border-slate-100 pb-3">
              <button
                type="button"
                onClick={() => setOpenSections((s) => ({ ...s, step3: !s.step3 }))}
                className="flex items-center gap-2.5 text-left focus:outline-none"
                aria-expanded={openSections.step3}
              >
                <span className="flex h-6 w-6 items-center justify-center rounded-full bg-brand-600 text-xs font-bold text-white shadow-xs">
                  3
                </span>
                <h2 className="text-base font-semibold text-slate-900">Service &amp; Options</h2>
                <svg
                  className={`h-4 w-4 text-slate-400 transition-transform duration-200 ${
                    openSections.step3 ? "rotate-180" : ""
                  }`}
                  fill="none"
                  viewBox="0 0 24 24"
                  stroke="currentColor"
                >
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 9l-7 7-7-7" />
                </svg>
              </button>
            </div>

            {openSections.step3 ? (
              <div className="space-y-4">
                {/* Service Selection */}
                <Field id="serviceLevel" label="Service" error={errors.serviceLevel}>
                  <select
                    id="serviceLevel"
                    name="serviceLevel"
                    className="input text-sm"
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

                {/* Quick Service Cards matching UI */}
                <div className="grid gap-3 sm:grid-cols-2">
                  <button
                    type="button"
                    onClick={() => update("serviceLevel", "STANDARD")}
                    className={`flex items-center gap-3 rounded-xl border p-3.5 text-left transition ${
                      values.serviceLevel === "STANDARD"
                        ? "border-brand-500 bg-brand-50/30 ring-2 ring-brand-500/20"
                        : "border-slate-200 hover:border-slate-300 bg-white"
                    }`}
                  >
                    <div
                      className={`flex h-4 w-4 shrink-0 items-center justify-center rounded-full border ${
                        values.serviceLevel === "STANDARD" ? "border-brand-600 bg-brand-600" : "border-slate-300"
                      }`}
                    >
                      {values.serviceLevel === "STANDARD" && <div className="h-1.5 w-1.5 rounded-full bg-white" />}
                    </div>
                    <div className="flex items-center gap-2.5">
                      <span className="flex h-8 w-8 items-center justify-center rounded-lg bg-slate-100 text-slate-600">
                        <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                          <path
                            strokeLinecap="round"
                            strokeLinejoin="round"
                            strokeWidth={2}
                            d="M13 16V6a1 1 0 00-1-1H4a1 1 0 00-1 1v10a1 1 0 001 1h1m8-1a1 1 0 01-1 1H9m4-1V8a1 1 0 011-1h2.586a1 1 0 01.707.293l3.414 3.414a1 1 0 01.293.707V16a1 1 0 01-1 1h-1m-6-1a1 1 0 001 1h1M5 17a2 2 0 104 0m-4 0a2 2 0 114 0m6 0a2 2 0 104 0m-4 0a2 2 0 114 0"
                          />
                        </svg>
                      </span>
                      <div>
                        <p className="font-semibold text-slate-900 text-sm">Standard</p>
                        <p className="text-xs text-slate-500">1 &ndash; 2 working days</p>
                      </div>
                    </div>
                  </button>

                  <button
                    type="button"
                    onClick={() => update("serviceLevel", "EXPRESS")}
                    className={`flex items-center gap-3 rounded-xl border p-3.5 text-left transition ${
                      values.serviceLevel === "EXPRESS"
                        ? "border-brand-500 bg-brand-50/30 ring-2 ring-brand-500/20"
                        : "border-slate-200 hover:border-slate-300 bg-white"
                    }`}
                  >
                    <div
                      className={`flex h-4 w-4 shrink-0 items-center justify-center rounded-full border ${
                        values.serviceLevel === "EXPRESS" ? "border-brand-600 bg-brand-600" : "border-slate-300"
                      }`}
                    >
                      {values.serviceLevel === "EXPRESS" && <div className="h-1.5 w-1.5 rounded-full bg-white" />}
                    </div>
                    <div className="flex items-center gap-2.5">
                      <span className="flex h-8 w-8 items-center justify-center rounded-lg bg-amber-50 text-amber-500">
                        <svg className="h-4 w-4" fill="currentColor" viewBox="0 0 20 20">
                          <path
                            fillRule="evenodd"
                            d="M11.3 1.046A1 1 0 0112 2v5h4a1 1 0 01.82 1.573l-7 10A1 1 0 018 18v-5H4a1 1 0 01-.82-1.573l7-10a1 1 0 011.12-.38z"
                            clipRule="evenodd"
                          />
                        </svg>
                      </span>
                      <div>
                        <p className="font-semibold text-slate-900 text-sm">Express</p>
                        <p className="text-xs text-slate-500">Same / next day</p>
                      </div>
                    </div>
                  </button>
                </div>

                {/* Same day vehicle selection if SAME_DAY is chosen */}
                {values.serviceLevel === "SAME_DAY" ? (
                  <Field id="vehicleType" label="Vehicle" error={errors.vehicleType}>
                    <select
                      id="vehicleType"
                      name="vehicleType"
                      className="input text-sm"
                      value={values.vehicleType}
                      onChange={(event) => update("vehicleType", event.target.value)}
                      aria-invalid={Boolean(errors.vehicleType)}
                      aria-describedby={errors.vehicleType ? "vehicleType-error" : undefined}
                    >
                      {VEHICLE_TYPES.map((type) => (
                        <option key={type} value={type}>
                          {type === "VAN" ? "Delivery van (VAN)" : "Motorbike (BIKE)"}
                        </option>
                      ))}
                    </select>
                  </Field>
                ) : null}

                {/* Cash on Delivery (COD) & Declared Value */}
                <div className="grid gap-4 sm:grid-cols-2">
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
                      placeholder="e.g. 5,000"
                      className="input text-sm"
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
                    hint="Insure the parcel for what it is worth. Leave blank to send without cover."
                  >
                    <input
                      id="declaredValue"
                      name="declaredValue"
                      type="number"
                      inputMode="decimal"
                      min="0"
                      step="0.01"
                      placeholder="e.g. 25,000"
                      className="input text-sm"
                      value={values.declaredValue}
                      onChange={(event) => update("declaredValue", event.target.value)}
                      aria-invalid={Boolean(errors.declaredValue)}
                      aria-describedby={errors.declaredValue ? "declaredValue-error" : "declaredValue-hint"}
                    />
                  </Field>
                </div>

                {/* Optional Measurements */}
                <fieldset className="space-y-2 border-t border-slate-100 pt-3">
                  <legend className="text-sm font-medium text-slate-700">Parcel measurements (optional)</legend>
                  <p id="dimensions-hint" className="text-xs text-slate-500">
                    A large, light parcel takes up room a heavy one does not, so measuring it gives a
                    truer price. Give all three or leave them blank.
                  </p>
                  <div className="grid gap-3 sm:grid-cols-3">
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

                {/* Special handling checkboxes if provided */}
                {specialHandling.length > 0 ? (
                  <fieldset className="space-y-2 border-t border-slate-100 pt-3">
                    <legend className="text-sm font-medium text-slate-700">Special handling (optional)</legend>
                    <div className="flex flex-wrap gap-2">
                      {specialHandling.map((option) => (
                        <label
                          key={option.code}
                          className="inline-flex items-center gap-1.5 rounded-lg border border-slate-200 bg-white px-3 py-1.5 text-xs text-slate-700 cursor-pointer hover:bg-slate-50 transition"
                        >
                          <input
                            type="checkbox"
                            name="specialHandling"
                            value={option.code}
                            checked={values.specialHandling.includes(option.code)}
                            onChange={(event) => toggleHandling(option.code, event.target.checked)}
                            className="h-3.5 w-3.5 rounded border-slate-300 text-brand-600 focus:ring-brand-500"
                          />
                          <span>{option.label}</span>
                        </label>
                      ))}
                    </div>
                    {errors.specialHandling ? <p className="field-error">{errors.specialHandling}</p> : null}
                  </fieldset>
                ) : null}

                {/* Submit Action */}
                <div className="pt-2">
                  <button
                    className="btn-primary flex items-center justify-center gap-2 px-6 py-2.5 text-sm font-semibold shadow-sm w-full sm:w-auto"
                    type="submit"
                    disabled={loading || districts.length === 0}
                  >
                    <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                      <path
                        strokeLinecap="round"
                        strokeLinejoin="round"
                        strokeWidth={2}
                        d="M9 7h6m0 10v-3m-3 3h.01M9 17h.01M9 14h.01M12 14h.01M15 11h.01M12 11h.01M9 11h.01M7 21h10a2 2 0 002-2V5a2 2 0 00-2-2H7a2 2 0 00-2 2v14a2 2 0 002 2z"
                      />
                    </svg>
                    <span>{loading ? "Calculating..." : quote ? "Recalculate" : "Get estimate"}</span>
                  </button>
                </div>
              </div>
            ) : null}
          </section>
        </form>

        {/* Right Column: Reference-Matched Dark Navy & White Quote Summary Card */}
        <aside className="space-y-4 lg:sticky lg:top-6 lg:self-start">
          <div aria-live="polite" aria-atomic="true">
            {loading ? (
              <div className="rounded-2xl border border-slate-200 bg-white p-8 text-center shadow-xs">
                <div className="mx-auto h-8 w-8 animate-spin rounded-full border-2 border-brand-600 border-t-transparent" />
                <p className="mt-3 text-sm font-medium text-slate-700">Calculating your fee...</p>
                <p className="mt-1 text-xs text-slate-500">Querying real-time rate cards and distance bands</p>
              </div>
            ) : null}

            {status === "failed" && message ? (
              <p role="alert" className="notice-error">
                {message}
              </p>
            ) : null}

            {!quote ? (
              <div className="overflow-hidden rounded-2xl bg-white shadow-sm ring-1 ring-slate-200 space-y-0">
                {/* Dark Navy Hero Header matching reference screenshot */}
                <div className="relative overflow-hidden bg-brand-950 p-6 text-white">
                  <div className="pointer-events-none absolute right-4 top-4 text-blue-400/20">
                    <IsometricBoxIcon className="h-24 w-24" />
                  </div>
                  <div className="relative">
                    <div className="flex items-center justify-between gap-2">
                      <p className="text-xs font-semibold uppercase tracking-wider text-blue-200/80">
                        Estimated fee
                      </p>
                      <span className="rounded-full bg-blue-500/20 px-2.5 py-0.5 text-[11px] font-semibold text-blue-200">
                        Awaiting details
                      </span>
                    </div>
                    <p className="mt-2 text-4xl font-extrabold tracking-tight text-white tabular-nums sm:text-5xl">
                      LKR &mdash;.&mdash;
                    </p>
                    <p className="mt-2 text-xs text-slate-300">
                      {values.fromDistrict && values.toDistrict
                        ? `${values.fromDistrict} to ${values.toDistrict}`
                        : "Select pickup & delivery locations to calculate"}
                    </p>
                  </div>
                </div>

                {/* White Body: Price Breakdown, Total Bar, Transit & Disclaimer */}
                <div className="p-6 space-y-4">
                  <div>
                    <h3 className="text-sm font-bold text-slate-900">Price Breakdown</h3>
                    <ul className="mt-2 divide-y divide-slate-100 text-xs">
                      <li className="flex items-center justify-between py-2 text-slate-500">
                        <span className="flex items-center gap-1.5">
                          <span className="h-1.5 w-1.5 rounded-full bg-slate-300" />
                          Base charge ({values.fromDistrict || "Pickup"} &rarr; {values.toDistrict || "Delivery"})
                        </span>
                        <span className="font-medium text-slate-400">Calculated on quote</span>
                      </li>
                      <li className="flex items-center justify-between py-2 text-slate-500">
                        <span className="flex items-center gap-1.5">
                          <span className="h-1.5 w-1.5 rounded-full bg-slate-300" />
                          Weight charge ({values.weightKg ? `${values.weightKg} kg` : "Based on weight"})
                        </span>
                        <span className="font-medium text-slate-400">Rate tier per kg</span>
                      </li>
                      <li className="flex items-center justify-between py-2 text-slate-500">
                        <span className="flex items-center gap-1.5">
                          <span className="h-1.5 w-1.5 rounded-full bg-slate-300" />
                          Service level ({values.serviceLevel === "EXPRESS" ? "Express" : "Standard"})
                        </span>
                        <span className="font-medium text-slate-400">
                          {values.serviceLevel === "EXPRESS" ? "Priority dispatch" : "Standard rate"}
                        </span>
                      </li>
                      {values.codAmount && Number(values.codAmount) > 0 ? (
                        <li className="flex items-center justify-between py-2 text-slate-500">
                          <span className="flex items-center gap-1.5">
                            <span className="h-1.5 w-1.5 rounded-full bg-slate-300" />
                            Cash on Delivery fee
                          </span>
                          <span className="font-medium text-slate-400">1.5% collection fee</span>
                        </li>
                      ) : null}
                    </ul>
                  </div>

                  {/* Highlighted Total Bar matching reference screenshot */}
                  <div className="flex items-center justify-between rounded-xl bg-blue-50/80 px-4 py-3 ring-1 ring-blue-100/70">
                    <span className="font-semibold text-slate-900 text-sm">Total (estimated)</span>
                    <span className="font-bold text-brand-700 text-lg tabular-nums">
                      LKR &mdash;.&mdash;
                    </span>
                  </div>

                  {/* Estimated Delivery Time Box matching reference screenshot */}
                  <div className="flex items-center gap-3 rounded-xl border border-blue-100 bg-blue-50/40 p-3.5">
                    <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-brand-100 text-brand-700">
                      <svg className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                        <path
                          strokeLinecap="round"
                          strokeLinejoin="round"
                          strokeWidth={1.75}
                          d="M8 7V3m8 4V3m-9 8h10M5 21h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z"
                        />
                      </svg>
                    </div>
                    <div>
                      <p className="text-[11px] font-medium text-slate-500">Estimated delivery</p>
                      <p className="text-xs font-bold text-slate-900">
                        {values.serviceLevel === "EXPRESS" ? "Same / next day" : "1 – 2 working days"}
                      </p>
                    </div>
                  </div>

                  {/* Test hook notice */}
                  <p className="notice-info text-xs">
                    Pick two districts and a weight to see the price before you sign up.
                  </p>

                  {/* Trust / Service Highlights */}
                  <div className="grid gap-2 rounded-xl bg-slate-50 p-3 text-xs text-slate-600">
                    <div className="flex items-center gap-2">
                      <span className="flex h-5 w-5 shrink-0 items-center justify-center rounded-full bg-emerald-100 text-emerald-700 font-bold text-[10px]">
                        &#10003;
                      </span>
                      <span>Island-wide coverage across all 25 districts</span>
                    </div>
                    <div className="flex items-center gap-2">
                      <span className="flex h-5 w-5 shrink-0 items-center justify-center rounded-full bg-emerald-100 text-emerald-700 font-bold text-[10px]">
                        &#10003;
                      </span>
                      <span>Real-time courier GPS tracking &amp; SMS alerts</span>
                    </div>
                    <div className="flex items-center gap-2">
                      <span className="flex h-5 w-5 shrink-0 items-center justify-center rounded-full bg-emerald-100 text-emerald-700 font-bold text-[10px]">
                        &#10003;
                      </span>
                      <span>Free cash on delivery (COD) remittance</span>
                    </div>
                  </div>

                  {/* Disclaimer Note matching reference screenshot */}
                  <p className="flex items-start gap-1.5 text-[11px] leading-relaxed text-slate-500">
                    <span className="mt-0.5 text-slate-400 font-bold">&#9432;</span>
                    <span>
                      Final price may vary slightly based on the exact location, parcel details and service availability.
                    </span>
                  </p>
                </div>
              </div>
            ) : null}

            {quote && lifetime ? (
              <QuoteSummary
                quote={quote}
                values={values}
                expired={expired}
                remaining={lifetime.label}
                quantity={values.numberOfParcels}
              />
            ) : null}
          </div>
        </aside>
      </div>
    </div>
  );
}

function QuoteSummary({
  quote,
  values,
  expired,
  remaining,
  quantity,
}: {
  quote: Quote;
  values: QuoteFormValues;
  expired: boolean;
  remaining: string;
  quantity: number;
}) {
  const billedWeight = weightExplanation(quote);
  const reclassified = sizeClassNotice(quote);
  const estimatedRoute = distanceNotice(quote);
  const badge = distanceBadge(quote);
  const transit = transitDaysLabel(quote.estimatedTransitDays);
  const chargeable = quote.chargeableWeightKg ?? quote.weightKg;

  const singleFee = quote.fee.amount;
  const totalFee = Math.round(singleFee * quantity * 100) / 100;

  return (
    <div className="overflow-hidden rounded-2xl bg-white shadow-md ring-1 ring-slate-200/90 space-y-0">
      {/* Dark Navy Hero Header matching reference UI */}
      <div className="relative overflow-hidden bg-brand-950 p-6 text-white">
        <div className="pointer-events-none absolute right-4 top-4 text-blue-400/25">
          <IsometricBoxIcon className="h-24 w-24" />
        </div>

        <div className="relative">
          <div className="flex items-center justify-between gap-2">
            <p className="text-xs font-semibold uppercase tracking-wider text-blue-200/80">
              Estimated fee
            </p>
            <span
              className={`rounded-full px-2.5 py-0.5 text-[11px] font-semibold ${
                expired ? "bg-amber-500/20 text-amber-300" : "bg-emerald-500/20 text-emerald-300"
              }`}
            >
              {expired ? "Estimate expired" : `Valid for ${remaining}`}
            </span>
          </div>

          <p className="mt-2 text-4xl font-extrabold tracking-tight text-white tabular-nums sm:text-5xl">
            {formatMoney(totalFee, quote.fee.currency)}
          </p>

          <p className="mt-2 text-xs text-slate-300">
            Zone {quote.fromZone} to zone {quote.toZone}
            {quantity > 1 ? ` · ${quantity} parcels` : ""}
          </p>
        </div>
      </div>

      {/* White Body: Price Breakdown, Total Bar, Transit & Disclaimer */}
      <div className="p-6 space-y-4">
        <div>
          <h3 className="text-sm font-bold text-slate-900">Price Breakdown</h3>
          <ul className="mt-2 divide-y divide-slate-100 text-xs">
            {visibleBreakdown(quote.breakdown).map((line) => {
              const label = breakdownLabel(line.rule);
              return (
                <li key={line.rule} className="flex items-center justify-between py-2 text-slate-700">
                  <div className="flex items-center gap-1">
                    <span>{label}</span>
                    {line.rule === "BASE" && quote.fromDistrict && quote.toDistrict ? (
                      <span className="text-slate-400 font-normal">
                        ({quote.fromDistrict} &rarr; {quote.toDistrict})
                      </span>
                    ) : null}
                    {line.rule === "WEIGHT" && chargeable ? (
                      <span className="text-slate-400 font-normal">({chargeable} kg)</span>
                    ) : null}
                  </div>
                  <span className="font-semibold tabular-nums text-slate-900">
                    {formatMoney(line.amount * quantity, quote.fee.currency)}
                  </span>
                </li>
              );
            })}
          </ul>
        </div>

        {/* Highlighted Total Bar matching reference screenshot */}
        <div className="flex items-center justify-between rounded-xl bg-blue-50/80 px-4 py-3 ring-1 ring-blue-100/70">
          <span className="font-semibold text-slate-900 text-sm">Total (estimated)</span>
          <span className="font-bold text-brand-700 text-lg tabular-nums">
            {formatMoney(totalFee, quote.fee.currency)}
          </span>
        </div>

        {/* Estimated Delivery Time Box matching reference screenshot */}
        <div className="flex items-center gap-3 rounded-xl border border-blue-100 bg-blue-50/40 p-3.5">
          <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-brand-100 text-brand-700">
            <svg className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                strokeWidth={1.75}
                d="M8 7V3m8 4V3m-9 8h10M5 21h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z"
              />
            </svg>
          </div>
          <div>
            <p className="text-[11px] font-medium text-slate-500">Estimated delivery</p>
            <p className="text-xs font-bold text-slate-900">
              {quote.serviceLevel === "EXPRESS" ? "Same / next day" : "1 – 2 working days"}
            </p>
          </div>
        </div>

        {/* Route / Weight notices */}
        {billedWeight ? <p className="notice-info text-xs">{billedWeight}</p> : null}
        {reclassified ? <p className="notice-info text-xs">{reclassified}</p> : null}
        {estimatedRoute ? <p className="notice-warning text-xs">{estimatedRoute}</p> : null}

        <dl className="grid grid-cols-2 gap-3 text-xs">
          {quote.distanceKm ? (
            <div>
              <dt className="text-slate-600">Distance</dt>
              <dd>
                {quote.distanceKm} km {quote.distanceSource ? <span className="badge">{badge}</span> : null}
              </dd>
            </div>
          ) : null}
          {transit ? (
            <div>
              <dt className="text-slate-600">Estimated transit</dt>
              <dd>{transit}</dd>
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

        {expired ? (
          <p role="alert" className="notice-warning text-xs">
            Prices can change, so this estimate only lasts a few minutes. Recalculate to continue.
          </p>
        ) : (
          <p className="break-all text-xs text-slate-500">Reference {quote.quoteId}</p>
        )}
        {quote.pricingVersion ? (
          <p className="text-xs text-slate-500">Pricing version {quote.pricingVersion}</p>
        ) : null}

        {/* Disclaimer Note matching reference screenshot */}
        <p className="flex items-start gap-1.5 text-[11px] leading-relaxed text-slate-500">
          <span className="mt-0.5 text-slate-400 font-bold">&#9432;</span>
          <span>
            Final price may vary slightly based on the exact location, parcel details and service availability.
          </span>
        </p>

        {/* Direct Action to Booking */}
        <div className="space-y-2 border-t border-slate-100 pt-2">
          {quantity > 1 ? (
            <p className="text-[11px] text-slate-500">
              Each booking creates one parcel per quote. Proceeding will pre-fill details for a single parcel.
            </p>
          ) : null}
          <Link
            href={quoteToBookingHref(values, quote)}
            className="btn-primary w-full py-2.5 text-center text-xs font-semibold shadow-xs"
          >
            Book this delivery &rarr;
          </Link>
        </div>
      </div>
    </div>
  );
}

function vehicleTypeLabel(type: string): string {
  return type === "VAN" ? "Van" : type === "BIKE" ? "Bike" : type;
}

function IsometricBoxIcon({ className = "h-16 w-16" }: { className?: string }) {
  return (
    <svg className={className} viewBox="0 0 100 100" fill="none" stroke="currentColor" strokeWidth="2.5">
      <path d="M50 15 L85 33 L50 52 L15 33 Z" />
      <path d="M15 33 L15 67 L50 85 L50 52 Z" />
      <path d="M85 33 L85 67 L50 85 L50 52 Z" />
      <path d="M50 52 L50 85" />
      <path d="M15 33 L50 52 L85 33" />
      <path d="M32 24 L67 43" strokeDasharray="3 3" opacity="0.6" />
    </svg>
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
        className="input text-xs"
        value={value}
        onChange={(event) => onChange(event.target.value)}
        aria-invalid={Boolean(error)}
        aria-describedby={error ? `${id}-error` : "dimensions-hint"}
      />
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
