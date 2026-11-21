"use client";

import dynamic from "next/dynamic";
import Link from "next/link";
import { useEffect, useMemo, useState } from "react";
import type { SavedAddress } from "@/lib/addresses";
import type { MapConfig } from "@/lib/map-config";
import {
  SRI_LANKA_DISTRICTS,
  isValidSriLankanPhone,
  serviceLevelDescription,
  sizeClassDescription,
  type ParcelResponse,
  type ServiceLevel,
  type SizeClass,
} from "@/lib/parcels";
import {
  MAX_SIDE_CM,
  MAX_SPECIAL_HANDLING,
  MAX_WEIGHT_KG,
  SERVICE_LEVELS,
  SIZE_CLASSES,
  VEHICLE_TYPES,
  breakdownLabel,
  distanceBadge,
  distanceNotice,
  districtOptions,
  formatMoney,
  pricingErrorMessage,
  quoteLifetime,
  serviceLevelLabel,
  sizeClassLabel,
  sizeClassNotice,
  transitDaysLabel,
  validateQuoteForm,
  visibleBreakdown,
  weightExplanation,
  type GeoPick,
  type LocatedPoint,
  type Quote,
  type QuoteFormValues,
  type SpecialHandlingOption,
  type VehicleType,
  type Zone,
} from "@/lib/pricing";

const LocationPicker = dynamic(
  () => import("@/components/location-picker").then((module) => module.LocationPicker),
  {
    ssr: false,
    loading: () => <p className="text-xs text-slate-600">Loading map...</p>,
  },
);

type Step = 1 | 2 | 3 | 4 | 5 | 6;

interface BookDeliveryWizardProps {
  zones: Zone[];
  specialHandling?: SpecialHandlingOption[];
  coverageError?: string;
  map?: MapConfig;
  initialPricing?: Partial<QuoteFormValues>;
}

interface FormState {
  // Step 1: Pickup
  pickupSavedId: string;
  pickupLine1: string;
  pickupLine2: string;
  pickupCity: string;
  pickupDistrict: string;
  pickupPostalCode: string;
  fromPoint?: GeoPick;

  // Step 2: Dropoff & Recipient
  recipientName: string;
  recipientPhone: string;
  recipientEmail: string;
  dropoffLine1: string;
  dropoffLine2: string;
  dropoffCity: string;
  dropoffDistrict: string;
  dropoffPostalCode: string;
  toPoint?: GeoPick;
  notes: string;

  // Step 3: Package specs & Pricing options
  weightKg: string;
  sizeClass: SizeClass;
  serviceLevel: ServiceLevel;
  vehicleType: VehicleType;
  lengthCm: string;
  widthCm: string;
  heightCm: string;
  codAmount: string;
  declaredValue: string;
  specialHandling: string[];
}

function buildInitialForm(initialPricing?: Partial<QuoteFormValues>): FormState {
  const size = initialPricing?.sizeClass;
  const service = initialPricing?.serviceLevel;
  const vehicle = initialPricing?.vehicleType;

  return {
    pickupSavedId: "",
    pickupLine1: initialPricing?.fromAddress ?? "",
    pickupLine2: "",
    pickupCity: "",
    pickupDistrict: initialPricing?.fromDistrict ?? "Colombo",
    pickupPostalCode: "",
    fromPoint: initialPricing?.fromPoint,

    recipientName: "",
    recipientPhone: "",
    recipientEmail: "",
    dropoffLine1: initialPricing?.toAddress ?? "",
    dropoffLine2: "",
    dropoffCity: "",
    dropoffDistrict: initialPricing?.toDistrict ?? "Kandy",
    dropoffPostalCode: "",
    toPoint: initialPricing?.toPoint,
    notes: "",

    weightKg: initialPricing?.weightKg ?? "1.5",
    sizeClass: size && (SIZE_CLASSES as readonly string[]).includes(size) ? (size as SizeClass) : "S",
    serviceLevel:
      service && (SERVICE_LEVELS as readonly string[]).includes(service) ? (service as ServiceLevel) : "STANDARD",
    vehicleType: vehicle && (VEHICLE_TYPES as readonly string[]).includes(vehicle) ? (vehicle as VehicleType) : "BIKE",
    lengthCm: initialPricing?.lengthCm ?? "",
    widthCm: initialPricing?.widthCm ?? "",
    heightCm: initialPricing?.heightCm ?? "",
    codAmount: initialPricing?.codAmount ?? "",
    declaredValue: initialPricing?.declaredValue ?? "",
    specialHandling: initialPricing?.specialHandling ?? [],
  };
}

function toQuoteFormValues(form: FormState): QuoteFormValues {
  return {
    fromAddress: form.pickupLine1,
    fromDistrict: form.pickupDistrict,
    toAddress: form.dropoffLine1,
    toDistrict: form.dropoffDistrict,
    weightKg: form.weightKg,
    sizeClass: form.sizeClass,
    numberOfParcels: 1,
    serviceLevel: form.serviceLevel,
    codAmount: form.codAmount,
    declaredValue: form.declaredValue,
    lengthCm: form.lengthCm,
    widthCm: form.widthCm,
    heightCm: form.heightCm,
    vehicleType: form.vehicleType,
    specialHandling: form.specialHandling,
    fromPoint: form.fromPoint,
    toPoint: form.toPoint,
  };
}

function vehicleTypeLabel(type: VehicleType | string): string {
  switch (type) {
    case "BIKE":
      return "Motorbike (compact same-day parcel)";
    case "VAN":
      return "Delivery Van (bulky or longer route)";
    default:
      return type;
  }
}

export function BookDeliveryWizard({
  zones,
  specialHandling = [],
  coverageError,
  map,
  initialPricing,
}: BookDeliveryWizardProps) {
  const [step, setStep] = useState<Step>(1);
  const [form, setForm] = useState<FormState>(() => buildInitialForm(initialPricing));
  const [savedAddresses, setSavedAddresses] = useState<SavedAddress[]>([]);

  // Expand optional panels automatically when pre-filled from /quote
  const [showPickupMap, setShowPickupMap] = useState(false);
  const [isPickupMapMax, setIsPickupMapMax] = useState(false);
  const [showDropoffMap, setShowDropoffMap] = useState(false);
  const [isDropoffMapMax, setIsDropoffMapMax] = useState(false);
  const [showDimensions, setShowDimensions] = useState(
    Boolean(initialPricing?.lengthCm || initialPricing?.widthCm || initialPricing?.heightCm),
  );
  const [showAddons, setShowAddons] = useState(
    Boolean(
      initialPricing?.codAmount ||
        initialPricing?.declaredValue ||
        (initialPricing?.specialHandling && initialPricing.specialHandling.length > 0),
    ),
  );

  // Quote state
  const [quote, setQuote] = useState<Quote | null>(null);
  const [quoting, setQuoting] = useState(false);
  const [quoteError, setQuoteError] = useState<string | null>(null);
  const [now, setNow] = useState(() => new Date());

  // Booking submit state
  const [submitting, setSubmitting] = useState(false);
  const [bookingError, setBookingError] = useState<string | null>(null);
  const [bookedParcel, setBookedParcel] = useState<ParcelResponse | null>(null);
  const [copied, setCopied] = useState(false);

  // Validation errors
  const [errors, setErrors] = useState<Record<string, string>>({});

  const apiDistricts = useMemo(() => districtOptions(zones), [zones]);
  const districts = useMemo(
    () => (apiDistricts.length > 0 ? apiDistricts : [...SRI_LANKA_DISTRICTS]),
    [apiDistricts],
  );

  const hasPrefilledRoute = Boolean(initialPricing?.fromDistrict || initialPricing?.fromPoint);

  // Load saved addresses from user-service via BFF
  useEffect(() => {
    async function loadAddresses() {
      try {
        const res = await fetch("/api/bff/api/v1/users/me/addresses");
        if (res.ok) {
          const data = (await res.json()) as SavedAddress[];
          setSavedAddresses(data);
          const defaultAddr = data.find((a) => a.isDefault);
          if (defaultAddr && !hasPrefilledRoute) {
            setForm((prev) => ({
              ...prev,
              pickupSavedId: defaultAddr.id,
              pickupLine1: defaultAddr.line1,
              pickupLine2: defaultAddr.line2 || "",
              pickupCity: defaultAddr.city,
              pickupDistrict: defaultAddr.district,
              pickupPostalCode: defaultAddr.postalCode || "",
              fromPoint: undefined,
            }));
          }
        }
      } catch {
        // Fallback to manual entry
      }
    }
    loadAddresses();
  }, [hasPrefilledRoute]);

  // Live expiry countdown timer for quote
  useEffect(() => {
    if (!quote || step > 5) return;
    const interval = setInterval(() => setNow(new Date()), 1000);
    return () => clearInterval(interval);
  }, [quote, step]);

  const lifetime = quote ? quoteLifetime(quote.expiresAt, now) : undefined;
  const quoteExpired = lifetime?.expired ?? false;
  const timeRemaining = lifetime ? (lifetime.expired ? "Expired" : lifetime.label) : "";

  function handlePickupSelect(addressId: string) {
    if (!addressId) {
      setForm((prev) => ({ ...prev, pickupSavedId: "" }));
      return;
    }
    const found = savedAddresses.find((a) => a.id === addressId);
    if (found) {
      setForm((prev) => ({
        ...prev,
        pickupSavedId: found.id,
        pickupLine1: found.line1,
        pickupLine2: found.line2 || "",
        pickupCity: found.city,
        pickupDistrict: found.district,
        pickupPostalCode: found.postalCode || "",
        fromPoint: undefined,
      }));
      setErrors((prev) => ({
        ...prev,
        pickupLine1: "",
        pickupCity: "",
        pickupDistrict: "",
      }));
    }
  }

  function handleDistrictChange(side: "pickup" | "dropoff", district: string) {
    setForm((prev) => {
      if (side === "pickup") {
        const keepPoint = prev.pickupDistrict === district;
        return {
          ...prev,
          pickupDistrict: district,
          fromPoint: keepPoint ? prev.fromPoint : undefined,
        };
      }
      const keepPoint = prev.dropoffDistrict === district;
      return {
        ...prev,
        dropoffDistrict: district,
        toPoint: keepPoint ? prev.toPoint : undefined,
      };
    });
    setErrors((prev) => ({
      ...prev,
      [side === "pickup" ? "pickupDistrict" : "dropoffDistrict"]: "",
    }));
  }

  function handleMapPick(side: "pickup" | "dropoff", point: GeoPick, located: LocatedPoint) {
    setForm((prev) =>
      side === "pickup"
        ? { ...prev, pickupDistrict: located.district, fromPoint: point }
        : { ...prev, dropoffDistrict: located.district, toPoint: point },
    );
    setErrors((prev) => ({
      ...prev,
      [side === "pickup" ? "pickupDistrict" : "dropoffDistrict"]: "",
    }));
  }

  function toggleHandling(code: string, checked: boolean) {
    setForm((prev) => ({
      ...prev,
      specialHandling: checked
        ? [...prev.specialHandling, code]
        : prev.specialHandling.filter((existing) => existing !== code),
    }));
    setErrors((prev) => ({ ...prev, specialHandling: "" }));
  }

  function validateStep(s: Step): boolean {
    const errs: Record<string, string> = {};

    if (s === 1) {
      if (!form.pickupLine1.trim()) errs.pickupLine1 = "Street address is required.";
      if (!form.pickupCity.trim()) errs.pickupCity = "City is required.";
      if (!form.pickupDistrict.trim()) errs.pickupDistrict = "District is required.";
      else if (apiDistricts.length > 0 && !apiDistricts.includes(form.pickupDistrict.trim())) {
        errs.pickupDistrict = "We do not deliver from there yet.";
      }
      if (form.pickupPostalCode && !/^[0-9]{5}$/.test(form.pickupPostalCode.trim())) {
        errs.pickupPostalCode = "Postal code must be 5 digits.";
      }
    }

    if (s === 2) {
      if (!form.recipientName.trim()) errs.recipientName = "Recipient name is required.";
      if (!form.recipientPhone.trim()) {
        errs.recipientPhone = "Recipient mobile number is required.";
      } else if (!isValidSriLankanPhone(form.recipientPhone)) {
        errs.recipientPhone = "Must be a valid Sri Lankan mobile number (e.g. 0771234567 or +94771234567).";
      }
      if (form.recipientEmail && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.recipientEmail.trim())) {
        errs.recipientEmail = "Invalid email address format.";
      }
      if (!form.dropoffLine1.trim()) errs.dropoffLine1 = "Delivery street address is required.";
      if (!form.dropoffCity.trim()) errs.dropoffCity = "City is required.";
      if (!form.dropoffDistrict.trim()) errs.dropoffDistrict = "District is required.";
      else if (apiDistricts.length > 0 && !apiDistricts.includes(form.dropoffDistrict.trim())) {
        errs.dropoffDistrict = "We do not deliver there yet.";
      }
      if (form.dropoffPostalCode && !/^[0-9]{5}$/.test(form.dropoffPostalCode.trim())) {
        errs.dropoffPostalCode = "Postal code must be 5 digits.";
      }
    }

    if (s === 3) {
      const validation = validateQuoteForm(toQuoteFormValues(form), districts);
      if (!validation.ok) {
        if (validation.errors.fromDistrict) errs.pickupDistrict = validation.errors.fromDistrict;
        if (validation.errors.toDistrict) errs.dropoffDistrict = validation.errors.toDistrict;
        if (validation.errors.weightKg) errs.weightKg = validation.errors.weightKg;
        if (validation.errors.sizeClass) errs.sizeClass = validation.errors.sizeClass;
        if (validation.errors.serviceLevel) errs.serviceLevel = validation.errors.serviceLevel;
        if (validation.errors.vehicleType) errs.vehicleType = validation.errors.vehicleType;
        if (validation.errors.codAmount) errs.codAmount = validation.errors.codAmount;
        if (validation.errors.declaredValue) errs.declaredValue = validation.errors.declaredValue;
        if (validation.errors.lengthCm) errs.lengthCm = validation.errors.lengthCm;
        if (validation.errors.widthCm) errs.widthCm = validation.errors.widthCm;
        if (validation.errors.heightCm) errs.heightCm = validation.errors.heightCm;
        if (validation.errors.specialHandling) errs.specialHandling = validation.errors.specialHandling;

        if (errs.lengthCm || errs.widthCm || errs.heightCm) setShowDimensions(true);
        if (errs.codAmount || errs.declaredValue || errs.specialHandling) setShowAddons(true);
      }
    }

    setErrors(errs);
    return Object.keys(errs).length === 0;
  }

  async function fetchQuote() {
    const validation = validateQuoteForm(toQuoteFormValues(form), districts);
    if (!validation.ok) {
      validateStep(3);
      return;
    }

    setQuoting(true);
    setQuoteError(null);
    try {
      const res = await fetch("/api/bff/api/v1/pricing/quotes", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(validation.body),
      });

      if (!res.ok) {
        const errorData = (await res.json().catch(() => ({}))) as { code?: string; detail?: string };
        const friendly = pricingErrorMessage(errorData.code);
        throw new Error(friendly || errorData.detail || `Pricing service returned HTTP ${res.status}`);
      }

      const quoteData = (await res.json()) as Quote;
      setQuote(quoteData);
      setNow(new Date());
      setStep(4);
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : "Unable to calculate delivery fee quote.";
      setQuoteError(message);
    } finally {
      setQuoting(false);
    }
  }

  async function handleConfirmBooking() {
    if (!quote) return;
    setSubmitting(true);
    setBookingError(null);

    const payload = {
      quoteId: quote.quoteId,
      pickup: {
        line1: form.pickupLine1.trim(),
        line2: form.pickupLine2.trim() || undefined,
        city: form.pickupCity.trim(),
        district: form.pickupDistrict.trim(),
        postalCode: form.pickupPostalCode.trim() || undefined,
      },
      dropoff: {
        line1: form.dropoffLine1.trim(),
        line2: form.dropoffLine2.trim() || undefined,
        city: form.dropoffCity.trim(),
        district: form.dropoffDistrict.trim(),
        postalCode: form.dropoffPostalCode.trim() || undefined,
      },
      recipient: {
        name: form.recipientName.trim(),
        phone: form.recipientPhone.trim(),
        email: form.recipientEmail.trim() || undefined,
      },
      notes: form.notes.trim() || undefined,
    };

    try {
      const res = await fetch("/api/bff/api/v1/parcels", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(payload),
      });

      if (res.status === 201) {
        const parcel = (await res.json()) as ParcelResponse;
        setBookedParcel(parcel);
        setStep(6);
      } else {
        const errJson = await res.json().catch(() => ({}));
        if (res.status === 422 && errJson.code === "QUOTE_EXPIRED") {
          setBookingError("Your quote expired while reviewing. Please recalculate your fee quote.");
        } else if (res.status === 503) {
          setBookingError("Pricing service is currently unavailable. Please try again shortly.");
        } else {
          setBookingError(errJson.detail || `Booking failed with status ${res.status}`);
        }
      }
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : "Failed to connect to booking service.";
      setBookingError(message);
    } finally {
      setSubmitting(false);
    }
  }

  function handleNext() {
    if (step === 1 && validateStep(1)) {
      setStep(2);
    } else if (step === 2 && validateStep(2)) {
      setStep(3);
    } else if (step === 3 && validateStep(3)) {
      fetchQuote();
    } else if (step === 4 && !quoteExpired) {
      setStep(5);
    }
  }

  function handleBack() {
    if (step === 2) setStep(1);
    else if (step === 3) setStep(2);
    else if (step === 4) setStep(3);
    else if (step === 5) setStep(4);
  }

  function copyTrackingNumber(tn: string) {
    navigator.clipboard.writeText(tn);
    setCopied(true);
    setTimeout(() => setCopied(false), 2500);
  }

  const handlingLabelMap = useMemo(
    () => new Map(specialHandling.map((opt) => [opt.code, opt.label])),
    [specialHandling],
  );

  return (
    <div className="mx-auto max-w-3xl space-y-6">
      {/* Header */}
      <div className="flex flex-wrap items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold text-slate-900">Book a Delivery</h1>
          <p className="text-sm text-slate-600">
            Create a delivery request, review fee quote, and receive instant tracking number.
          </p>
        </div>
        <Link href="/customer" className="text-sm font-medium text-brand-600 hover:text-brand-700">
          &larr; Back to my deliveries
        </Link>
      </div>

      {coverageError ? (
        <div role="alert" className="notice-warning text-xs">
          {coverageError} Showing standard Sri Lanka districts as a temporary fallback.
        </div>
      ) : null}

      {/* Stepper Progress */}
      {step < 6 && (
        <div className="rounded-xl border border-slate-200 bg-white p-4 shadow-xs">
          <nav aria-label="Progress">
            <ol className="flex items-center justify-between text-xs sm:text-sm font-medium">
              {[
                { s: 1, label: "Pickup" },
                { s: 2, label: "Drop-off" },
                { s: 3, label: "Package" },
                { s: 4, label: "Quote" },
                { s: 5, label: "Confirm" },
              ].map(({ s, label }) => {
                const isCurrent = step === s;
                const isCompleted = step > s;
                return (
                  <li key={s} className="flex items-center gap-2">
                    <span
                      className={`flex h-7 w-7 items-center justify-center rounded-full text-xs font-bold ${
                        isCurrent
                          ? "bg-brand-600 text-white"
                          : isCompleted
                          ? "bg-emerald-500 text-white"
                          : "bg-slate-100 text-slate-500"
                      }`}
                    >
                      {isCompleted ? (
                        <svg
                          className="h-4 w-4"
                          fill="none"
                          viewBox="0 0 24 24"
                          stroke="currentColor"
                          strokeWidth={2.5}
                          aria-hidden="true"
                        >
                          <path strokeLinecap="round" strokeLinejoin="round" d="M5 13l4 4L19 7" />
                        </svg>
                      ) : (
                        s
                      )}
                    </span>
                    <span
                      className={`hidden sm:inline ${
                        isCurrent ? "font-bold text-brand-700" : isCompleted ? "text-slate-700" : "text-slate-400"
                      }`}
                    >
                      {label}
                    </span>
                  </li>
                );
              })}
            </ol>
          </nav>
        </div>
      )}

      {/* Step 1: Pickup Details */}
      {step === 1 && (
        <div className="card space-y-5">
          <div className="flex flex-wrap items-start justify-between gap-3">
            <div>
              <h2 className="text-lg font-bold text-slate-900">Step 1: Collection Address</h2>
              <p className="text-sm text-slate-600">Where should our courier collect the parcel from?</p>
            </div>
            {map ? (
              <button
                type="button"
                onClick={() => setShowPickupMap((prev) => !prev)}
                className="inline-flex items-center gap-1.5 rounded-lg border border-slate-200 bg-slate-50/80 px-2.5 py-1.5 text-xs font-medium text-slate-700 hover:bg-slate-100 transition"
              >
                <MapPinIcon className="h-3.5 w-3.5 text-brand-600" />
                <span>{showPickupMap ? "Hide map" : "Pick on map"}</span>
              </button>
            ) : null}
          </div>

          {showPickupMap && map ? (
            <BookingMapPanel
              id="bookingPickupMap"
              title="Select pickup location on map"
              purpose="collection"
              tileUrl={map.tileUrl}
              attribution={map.attribution}
              point={form.fromPoint}
              district={form.pickupDistrict}
              isMaximized={isPickupMapMax}
              onMaximizeChange={setIsPickupMapMax}
              onClose={() => {
                setIsPickupMapMax(false);
                setShowPickupMap(false);
              }}
              onPick={(point, located) => handleMapPick("pickup", point, located)}
            />
          ) : null}

          {/* Saved Addresses Selector */}
          {savedAddresses.length > 0 && (
            <div className="space-y-2 rounded-lg border border-brand-100 bg-brand-50/50 p-4">
              <label htmlFor="savedAddress" className="block text-sm font-semibold text-brand-900">
                Choose from Saved Addresses (Address Book)
              </label>
              <select
                id="savedAddress"
                className="input bg-white"
                value={form.pickupSavedId}
                onChange={(e) => handlePickupSelect(e.target.value)}
              >
                <option value="">-- Enter a new pickup address below --</option>
                {savedAddresses.map((addr) => (
                  <option key={addr.id} value={addr.id}>
                    {addr.label} ({addr.line1}, {addr.city}, {addr.district})
                  </option>
                ))}
              </select>
            </div>
          )}

          <div className="space-y-4">
            <div>
              <label htmlFor="pickupLine1" className="block text-sm font-medium text-slate-700">
                Street Address (Line 1) *
              </label>
              <input
                id="pickupLine1"
                type="text"
                className="input mt-1"
                placeholder="e.g. 45 Galle Road"
                value={form.pickupLine1}
                onChange={(e) => setForm({ ...form, pickupLine1: e.target.value })}
              />
              {errors.pickupLine1 && <p className="field-error">{errors.pickupLine1}</p>}
            </div>

            <div>
              <label htmlFor="pickupLine2" className="block text-sm font-medium text-slate-700">
                Address Line 2 (Optional)
              </label>
              <input
                id="pickupLine2"
                type="text"
                className="input mt-1"
                placeholder="e.g. Apt 3B, Floor 4"
                value={form.pickupLine2}
                onChange={(e) => setForm({ ...form, pickupLine2: e.target.value })}
              />
            </div>

            <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
              <div>
                <label htmlFor="pickupCity" className="block text-sm font-medium text-slate-700">
                  City *
                </label>
                <input
                  id="pickupCity"
                  type="text"
                  className="input mt-1"
                  placeholder="e.g. Colombo 03"
                  value={form.pickupCity}
                  onChange={(e) => setForm({ ...form, pickupCity: e.target.value })}
                />
                {errors.pickupCity && <p className="field-error">{errors.pickupCity}</p>}
              </div>

              <div>
                <label htmlFor="pickupDistrict" className="block text-sm font-medium text-slate-700">
                  District *
                </label>
                <select
                  id="pickupDistrict"
                  className="input mt-1"
                  value={form.pickupDistrict}
                  onChange={(e) => handleDistrictChange("pickup", e.target.value)}
                >
                  {districts.map((d) => (
                    <option key={d} value={d}>
                      {d}
                    </option>
                  ))}
                </select>
                {errors.pickupDistrict && <p className="field-error">{errors.pickupDistrict}</p>}
                {form.fromPoint ? (
                  <p className="mt-1 flex items-center justify-between text-[11px] text-slate-500">
                    <span>
                      Pinned: {form.fromPoint.latitude.toFixed(4)}, {form.fromPoint.longitude.toFixed(4)}
                    </span>
                    <button
                      type="button"
                      onClick={() => setForm((prev) => ({ ...prev, fromPoint: undefined }))}
                      className="text-brand-600 hover:underline"
                    >
                      Clear pin
                    </button>
                  </p>
                ) : null}
              </div>

              <div>
                <label htmlFor="pickupPostalCode" className="block text-sm font-medium text-slate-700">
                  Postal Code
                </label>
                <input
                  id="pickupPostalCode"
                  type="text"
                  maxLength={5}
                  className="input mt-1"
                  placeholder="e.g. 00300"
                  value={form.pickupPostalCode}
                  onChange={(e) => setForm({ ...form, pickupPostalCode: e.target.value })}
                />
                {errors.pickupPostalCode && <p className="field-error">{errors.pickupPostalCode}</p>}
              </div>
            </div>
          </div>

          <div className="flex justify-end border-t border-slate-100 pt-4">
            <button type="button" className="btn-primary" onClick={handleNext}>
              Next: Recipient &amp; Drop-off &rarr;
            </button>
          </div>
        </div>
      )}

      {/* Step 2: Drop-off & Recipient */}
      {step === 2 && (
        <div className="card space-y-5">
          <div>
            <h2 className="text-lg font-bold text-slate-900">Step 2: Recipient &amp; Delivery Destination</h2>
            <p className="text-sm text-slate-600">Who is receiving this package and where should it go?</p>
          </div>

          <div className="space-y-4">
            <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
              <div>
                <label htmlFor="recipientName" className="block text-sm font-medium text-slate-700">
                  Recipient Full Name *
                </label>
                <input
                  id="recipientName"
                  type="text"
                  className="input mt-1"
                  placeholder="e.g. Sunimal Perera"
                  value={form.recipientName}
                  onChange={(e) => setForm({ ...form, recipientName: e.target.value })}
                />
                {errors.recipientName && <p className="field-error">{errors.recipientName}</p>}
              </div>

              <div>
                <label htmlFor="recipientPhone" className="block text-sm font-medium text-slate-700">
                  Mobile Phone * (Sri Lanka mobile)
                </label>
                <input
                  id="recipientPhone"
                  type="tel"
                  className="input mt-1"
                  placeholder="0771234567 or +94771234567"
                  value={form.recipientPhone}
                  onChange={(e) => setForm({ ...form, recipientPhone: e.target.value })}
                />
                {errors.recipientPhone && <p className="field-error">{errors.recipientPhone}</p>}
              </div>
            </div>

            <div>
              <label htmlFor="recipientEmail" className="block text-sm font-medium text-slate-700">
                Recipient Email (Optional, for notifications)
              </label>
              <input
                id="recipientEmail"
                type="email"
                className="input mt-1"
                placeholder="e.g. sunimal@example.com"
                value={form.recipientEmail}
                onChange={(e) => setForm({ ...form, recipientEmail: e.target.value })}
              />
              {errors.recipientEmail && <p className="field-error">{errors.recipientEmail}</p>}
            </div>

            <div className="border-t border-slate-100 pt-3">
              <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
                <h3 className="text-sm font-semibold text-slate-800">Drop-off Street Address</h3>
                {map ? (
                  <button
                    type="button"
                    onClick={() => setShowDropoffMap((prev) => !prev)}
                    className="inline-flex items-center gap-1.5 rounded-lg border border-slate-200 bg-slate-50/80 px-2.5 py-1 text-xs font-medium text-slate-700 hover:bg-slate-100 transition"
                  >
                    <MapPinIcon className="h-3.5 w-3.5 text-brand-600" />
                    <span>{showDropoffMap ? "Hide map" : "Pick on map"}</span>
                  </button>
                ) : null}
              </div>

              {showDropoffMap && map ? (
                <div className="mb-4">
                  <BookingMapPanel
                    id="bookingDropoffMap"
                    title="Select delivery location on map"
                    purpose="delivery"
                    tileUrl={map.tileUrl}
                    attribution={map.attribution}
                    point={form.toPoint}
                    district={form.dropoffDistrict}
                    isMaximized={isDropoffMapMax}
                    onMaximizeChange={setIsDropoffMapMax}
                    onClose={() => {
                      setIsDropoffMapMax(false);
                      setShowDropoffMap(false);
                    }}
                    onPick={(point, located) => handleMapPick("dropoff", point, located)}
                  />
                </div>
              ) : null}

              <div className="space-y-4">
                <div>
                  <label htmlFor="dropoffLine1" className="block text-sm font-medium text-slate-700">
                    Street Address (Line 1) *
                  </label>
                  <input
                    id="dropoffLine1"
                    type="text"
                    className="input mt-1"
                    placeholder="e.g. 12 Temple Road"
                    value={form.dropoffLine1}
                    onChange={(e) => setForm({ ...form, dropoffLine1: e.target.value })}
                  />
                  {errors.dropoffLine1 && <p className="field-error">{errors.dropoffLine1}</p>}
                </div>

                <div>
                  <label htmlFor="dropoffLine2" className="block text-sm font-medium text-slate-700">
                    Address Line 2 (Optional)
                  </label>
                  <input
                    id="dropoffLine2"
                    type="text"
                    className="input mt-1"
                    placeholder="e.g. Near Clock Tower"
                    value={form.dropoffLine2}
                    onChange={(e) => setForm({ ...form, dropoffLine2: e.target.value })}
                  />
                </div>

                <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
                  <div>
                    <label htmlFor="dropoffCity" className="block text-sm font-medium text-slate-700">
                      City *
                    </label>
                    <input
                      id="dropoffCity"
                      type="text"
                      className="input mt-1"
                      placeholder="e.g. Kandy"
                      value={form.dropoffCity}
                      onChange={(e) => setForm({ ...form, dropoffCity: e.target.value })}
                    />
                    {errors.dropoffCity && <p className="field-error">{errors.dropoffCity}</p>}
                  </div>

                  <div>
                    <label htmlFor="dropoffDistrict" className="block text-sm font-medium text-slate-700">
                      District *
                    </label>
                    <select
                      id="dropoffDistrict"
                      className="input mt-1"
                      value={form.dropoffDistrict}
                      onChange={(e) => handleDistrictChange("dropoff", e.target.value)}
                    >
                      {districts.map((d) => (
                        <option key={d} value={d}>
                          {d}
                        </option>
                      ))}
                    </select>
                    {errors.dropoffDistrict && <p className="field-error">{errors.dropoffDistrict}</p>}
                    {form.toPoint ? (
                      <p className="mt-1 flex items-center justify-between text-[11px] text-slate-500">
                        <span>
                          Pinned: {form.toPoint.latitude.toFixed(4)}, {form.toPoint.longitude.toFixed(4)}
                        </span>
                        <button
                          type="button"
                          onClick={() => setForm((prev) => ({ ...prev, toPoint: undefined }))}
                          className="text-brand-600 hover:underline"
                        >
                          Clear pin
                        </button>
                      </p>
                    ) : null}
                  </div>

                  <div>
                    <label htmlFor="dropoffPostalCode" className="block text-sm font-medium text-slate-700">
                      Postal Code
                    </label>
                    <input
                      id="dropoffPostalCode"
                      type="text"
                      maxLength={5}
                      className="input mt-1"
                      placeholder="e.g. 20000"
                      value={form.dropoffPostalCode}
                      onChange={(e) => setForm({ ...form, dropoffPostalCode: e.target.value })}
                    />
                    {errors.dropoffPostalCode && <p className="field-error">{errors.dropoffPostalCode}</p>}
                  </div>
                </div>

                <div>
                  <label htmlFor="notes" className="block text-sm font-medium text-slate-700">
                    Delivery Instructions / Notes (Optional, max 300 chars)
                  </label>
                  <textarea
                    id="notes"
                    rows={2}
                    maxLength={300}
                    className="input mt-1"
                    placeholder="e.g. Please call before arriving or leave with security."
                    value={form.notes}
                    onChange={(e) => setForm({ ...form, notes: e.target.value })}
                  />
                </div>
              </div>
            </div>
          </div>

          <div className="flex items-center justify-between border-t border-slate-100 pt-4">
            <button type="button" className="btn-secondary" onClick={handleBack}>
              &larr; Back to Pickup
            </button>
            <button type="button" className="btn-primary" onClick={handleNext}>
              Next: Package Specs &rarr;
            </button>
          </div>
        </div>
      )}

      {/* Step 3: Package Specs & Pricing Options */}
      {step === 3 && (
        <div className="card space-y-5">
          <div>
            <h2 className="text-lg font-bold text-slate-900">Step 3: Package Specifications &amp; Options</h2>
            <p className="text-sm text-slate-600">
              Provide weight, size category, delivery speed, and optional protection or handling.
            </p>
          </div>

          <div className="space-y-5">
            <div>
              <label htmlFor="weightKg" className="block text-sm font-medium text-slate-700">
                Weight in Kilograms (kg) *
              </label>
              <input
                id="weightKg"
                type="number"
                inputMode="decimal"
                step="0.1"
                min="0.1"
                max={MAX_WEIGHT_KG}
                className="input mt-1 max-w-xs"
                value={form.weightKg}
                onChange={(e) => setForm({ ...form, weightKg: e.target.value })}
              />
              <p className="mt-1 text-xs text-slate-500">Up to {MAX_WEIGHT_KG} kg per parcel.</p>
              {errors.weightKg && <p className="field-error">{errors.weightKg}</p>}
            </div>

            <div>
              <label className="mb-2 block text-sm font-medium text-slate-700">Size Category *</label>
              <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
                {SIZE_CLASSES.map((size) => (
                  <label
                    key={size}
                    className={`flex cursor-pointer items-start gap-3 rounded-xl border p-3 transition-colors ${
                      form.sizeClass === size
                        ? "border-brand-600 bg-brand-50/40 text-brand-900 ring-1 ring-brand-600"
                        : "border-slate-200 bg-white text-slate-800 hover:bg-slate-50"
                    }`}
                  >
                    <input
                      type="radio"
                      name="sizeClass"
                      value={size}
                      checked={form.sizeClass === size}
                      onChange={() => setForm({ ...form, sizeClass: size })}
                      className="mt-1"
                    />
                    <div>
                      <span className="block text-sm font-bold">
                        {sizeClassLabel(size)} ({size})
                      </span>
                      <span className="text-xs text-slate-500">{sizeClassDescription(size)}</span>
                    </div>
                  </label>
                ))}
              </div>
              {errors.sizeClass && <p className="field-error">{errors.sizeClass}</p>}
            </div>

            {/* Collapsible Volumetric Dimensions */}
            <div className="rounded-xl border border-slate-200 bg-slate-50/60 p-4 space-y-3">
              <button
                type="button"
                onClick={() => setShowDimensions((prev) => !prev)}
                aria-expanded={showDimensions}
                className="flex w-full items-center justify-between text-left text-sm font-semibold text-slate-800"
              >
                <span>Exact parcel dimensions (optional)</span>
                <span className="text-xs font-medium text-brand-600">
                  {showDimensions ? "Hide" : "Add L x W x H"}
                </span>
              </button>
              {showDimensions ? (
                <div className="space-y-3 pt-1">
                  <p className="text-xs text-slate-500">
                    Large, light boxes are priced by volumetric space. Give all three measurements or leave all blank.
                  </p>
                  <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
                    <div>
                      <label htmlFor="lengthCm" className="block text-xs font-medium text-slate-700">
                        Length (cm)
                      </label>
                      <input
                        id="lengthCm"
                        type="number"
                        inputMode="decimal"
                        min="0.1"
                        max={MAX_SIDE_CM}
                        step="0.1"
                        className="input mt-1 bg-white text-sm"
                        value={form.lengthCm}
                        onChange={(e) => setForm({ ...form, lengthCm: e.target.value })}
                      />
                      {errors.lengthCm && <p className="field-error">{errors.lengthCm}</p>}
                    </div>
                    <div>
                      <label htmlFor="widthCm" className="block text-xs font-medium text-slate-700">
                        Width (cm)
                      </label>
                      <input
                        id="widthCm"
                        type="number"
                        inputMode="decimal"
                        min="0.1"
                        max={MAX_SIDE_CM}
                        step="0.1"
                        className="input mt-1 bg-white text-sm"
                        value={form.widthCm}
                        onChange={(e) => setForm({ ...form, widthCm: e.target.value })}
                      />
                      {errors.widthCm && <p className="field-error">{errors.widthCm}</p>}
                    </div>
                    <div>
                      <label htmlFor="heightCm" className="block text-xs font-medium text-slate-700">
                        Height (cm)
                      </label>
                      <input
                        id="heightCm"
                        type="number"
                        inputMode="decimal"
                        min="0.1"
                        max={MAX_SIDE_CM}
                        step="0.1"
                        className="input mt-1 bg-white text-sm"
                        value={form.heightCm}
                        onChange={(e) => setForm({ ...form, heightCm: e.target.value })}
                      />
                      {errors.heightCm && <p className="field-error">{errors.heightCm}</p>}
                    </div>
                  </div>
                </div>
              ) : null}
            </div>

            <div>
              <label className="mb-2 block text-sm font-medium text-slate-700">Service Level *</label>
              <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
                {SERVICE_LEVELS.map((level) => (
                  <label
                    key={level}
                    className={`flex cursor-pointer items-start gap-3 rounded-xl border p-3 transition-colors ${
                      form.serviceLevel === level
                        ? "border-brand-600 bg-brand-50/40 text-brand-900 ring-1 ring-brand-600"
                        : "border-slate-200 bg-white text-slate-800 hover:bg-slate-50"
                    }`}
                  >
                    <input
                      type="radio"
                      name="serviceLevel"
                      value={level}
                      checked={form.serviceLevel === level}
                      onChange={() => setForm({ ...form, serviceLevel: level })}
                      className="mt-1"
                    />
                    <div>
                      <span className="block text-sm font-bold">{serviceLevelLabel(level)}</span>
                      <span className="text-xs text-slate-500">{serviceLevelDescription(level)}</span>
                    </div>
                  </label>
                ))}
              </div>
              {errors.serviceLevel && <p className="field-error">{errors.serviceLevel}</p>}
            </div>

            {form.serviceLevel === "SAME_DAY" && (
              <div>
                <label htmlFor="vehicleType" className="block text-sm font-medium text-slate-700">
                  Same-Day Vehicle Type *
                </label>
                <select
                  id="vehicleType"
                  className="input mt-1 max-w-sm"
                  value={form.vehicleType}
                  onChange={(e) => setForm({ ...form, vehicleType: e.target.value as VehicleType })}
                >
                  {VEHICLE_TYPES.map((vt) => (
                    <option key={vt} value={vt}>
                      {vehicleTypeLabel(vt)}
                    </option>
                  ))}
                </select>
                {errors.vehicleType && <p className="field-error">{errors.vehicleType}</p>}
              </div>
            )}

            {/* Optional Add-ons: COD, Declared Value, Special Handling */}
            <div className="rounded-xl border border-slate-200 bg-slate-50/60 p-4 space-y-4">
              <button
                type="button"
                onClick={() => setShowAddons((prev) => !prev)}
                aria-expanded={showAddons}
                className="flex w-full items-center justify-between text-left text-sm font-semibold text-slate-800"
              >
                <span>Additional options: COD, Insurance &amp; Special handling (optional)</span>
                <span className="text-xs font-medium text-brand-600">
                  {showAddons ? "Hide" : "Configure"}
                </span>
              </button>

              {showAddons ? (
                <div className="space-y-4 pt-1">
                  <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
                    <div>
                      <label htmlFor="codAmount" className="block text-xs font-medium text-slate-700">
                        Cash to collect / COD (LKR)
                      </label>
                      <input
                        id="codAmount"
                        type="number"
                        inputMode="decimal"
                        min="0"
                        step="0.01"
                        placeholder="e.g. 5000"
                        className="input mt-1 bg-white text-sm"
                        value={form.codAmount}
                        onChange={(e) => setForm({ ...form, codAmount: e.target.value })}
                      />
                      <p className="mt-1 text-[11px] text-slate-500">
                        Leave blank if the recipient pays nothing on delivery.
                      </p>
                      {errors.codAmount && <p className="field-error">{errors.codAmount}</p>}
                    </div>

                    <div>
                      <label htmlFor="declaredValue" className="block text-xs font-medium text-slate-700">
                        Declared value / Insurance (LKR)
                      </label>
                      <input
                        id="declaredValue"
                        type="number"
                        inputMode="decimal"
                        min="0"
                        step="0.01"
                        placeholder="e.g. 25000"
                        className="input mt-1 bg-white text-sm"
                        value={form.declaredValue}
                        onChange={(e) => setForm({ ...form, declaredValue: e.target.value })}
                      />
                      <p className="mt-1 text-[11px] text-slate-500">
                        Insure the parcel for its declared value. Leave blank for no cover.
                      </p>
                      {errors.declaredValue && <p className="field-error">{errors.declaredValue}</p>}
                    </div>
                  </div>

                  {specialHandling.length > 0 ? (
                    <fieldset className="space-y-2 border-t border-slate-200 pt-3">
                      <legend className="text-xs font-medium text-slate-700">
                        Special handling (max {MAX_SPECIAL_HANDLING})
                      </legend>
                      <div className="flex flex-wrap gap-2">
                        {specialHandling.map((option) => (
                          <label
                            key={option.code}
                            className="inline-flex cursor-pointer items-center gap-1.5 rounded-lg border border-slate-200 bg-white px-3 py-1.5 text-xs text-slate-700 hover:bg-slate-50 transition"
                          >
                            <input
                              type="checkbox"
                              name="specialHandling"
                              value={option.code}
                              checked={form.specialHandling.includes(option.code)}
                              onChange={(event) => toggleHandling(option.code, event.target.checked)}
                              className="h-3.5 w-3.5 rounded border-slate-300 text-brand-600 focus:ring-brand-500"
                            />
                            <span>{option.label}</span>
                          </label>
                        ))}
                      </div>
                      {errors.specialHandling && <p className="field-error">{errors.specialHandling}</p>}
                    </fieldset>
                  ) : null}
                </div>
              ) : null}
            </div>
          </div>

          {quoteError && (
            <div className="rounded-lg border border-rose-200 bg-rose-50 p-4 text-sm text-rose-800">
              <p className="font-semibold">Unable to fetch quote</p>
              <p>{quoteError}</p>
            </div>
          )}

          <div className="flex items-center justify-between border-t border-slate-100 pt-4">
            <button type="button" className="btn-secondary" onClick={handleBack} disabled={quoting}>
              &larr; Back to Recipient
            </button>
            <button type="button" className="btn-primary" onClick={handleNext} disabled={quoting}>
              {quoting ? "Calculating Quote..." : "Get Fee Quote \u2192"}
            </button>
          </div>
        </div>
      )}

      {/* Step 4: Quoted Fee Breakdown */}
      {step === 4 && quote && (
        <div className="card space-y-5">
          <div className="flex flex-wrap items-center justify-between gap-2">
            <div>
              <h2 className="text-lg font-bold text-slate-900">Step 4: Delivery Fee Quote</h2>
              <p className="text-sm text-slate-600">
                Official delivery fee for route: {form.pickupDistrict} &rarr; {form.dropoffDistrict} (Zone {quote.fromZone}{" "}
                to Zone {quote.toZone}).
              </p>
            </div>
            <div
              className={`rounded-full border px-3 py-1 text-xs font-semibold ${
                quoteExpired
                  ? "border-rose-200 bg-rose-50 text-rose-800"
                  : "border-amber-200 bg-amber-50 text-amber-800"
              }`}
            >
              {quoteExpired ? "Quote expired" : `Valid for: ${timeRemaining}`}
            </div>
          </div>

          {/* Fee Card */}
          <div className="rounded-2xl border border-brand-100 bg-gradient-to-br from-brand-50 to-white p-6 shadow-xs">
            <div className="flex flex-wrap items-baseline justify-between gap-4 border-b border-brand-100 pb-4">
              <div>
                <span className="text-xs font-semibold uppercase tracking-wider text-brand-700">
                  Total Delivery Fee
                </span>
                <p className="text-3xl font-extrabold text-brand-900 tabular-nums">
                  {formatMoney(quote.fee.amount, quote.fee.currency)}
                </p>
              </div>
              <div className="text-right">
                <span className="text-xs text-slate-500">Quote ID</span>
                <p className="font-mono text-xs text-slate-700">{quote.quoteId.slice(0, 18)}...</p>
              </div>
            </div>

            <div className="mt-4 space-y-2 text-sm">
              <span className="block font-semibold text-slate-700">Fee Breakdown</span>
              <ul className="divide-y divide-slate-100">
                {visibleBreakdown(quote.breakdown).map((line) => {
                  const chargeable = quote.chargeableWeightKg ?? quote.weightKg;
                  return (
                    <li key={line.rule} className="flex justify-between py-1.5 text-slate-600">
                      <span>
                        {breakdownLabel(line.rule)}
                        {line.rule === "WEIGHT" && chargeable ? (
                          <span className="ml-1 text-xs text-slate-400">({chargeable} kg)</span>
                        ) : null}
                      </span>
                      <span className="font-medium tabular-nums text-slate-800">
                        {formatMoney(line.amount, quote.fee.currency)}
                      </span>
                    </li>
                  );
                })}
              </ul>
            </div>

            {/* Route & Weight notices */}
            <div className="mt-4 space-y-2">
              {weightExplanation(quote) ? <p className="notice-info text-xs">{weightExplanation(quote)}</p> : null}
              {sizeClassNotice(quote) ? <p className="notice-info text-xs">{sizeClassNotice(quote)}</p> : null}
              {distanceNotice(quote) ? <p className="notice-warning text-xs">{distanceNotice(quote)}</p> : null}
            </div>

            {/* Distance / Transit / Weight metadata */}
            <dl className="mt-4 grid grid-cols-2 gap-3 border-t border-brand-100/80 pt-3 text-xs sm:grid-cols-4">
              {quote.distanceKm ? (
                <div>
                  <dt className="text-slate-500">Distance</dt>
                  <dd className="font-medium text-slate-800">
                    {quote.distanceKm} km{" "}
                    {distanceBadge(quote) ? <span className="badge">{distanceBadge(quote)}</span> : null}
                  </dd>
                </div>
              ) : null}
              {transitDaysLabel(quote.estimatedTransitDays) ? (
                <div>
                  <dt className="text-slate-500">Estimated transit</dt>
                  <dd className="font-medium text-slate-800">{transitDaysLabel(quote.estimatedTransitDays)}</dd>
                </div>
              ) : null}
              <div>
                <dt className="text-slate-500">Actual weight</dt>
                <dd className="font-medium text-slate-800">{quote.weightKg} kg</dd>
              </div>
              <div>
                <dt className="text-slate-500">Chargeable weight</dt>
                <dd className="font-medium text-slate-800">{quote.chargeableWeightKg ?? quote.weightKg} kg</dd>
              </div>
            </dl>
          </div>

          {quoteExpired ? (
            <div className="flex flex-wrap items-center justify-between gap-3 rounded-lg border border-amber-200 bg-amber-50 p-3 text-xs text-amber-900">
              <span>This quote has expired. Recalculate to lock in a fresh 15-minute quote.</span>
              <button type="button" className="btn-secondary text-xs" onClick={fetchQuote} disabled={quoting}>
                {quoting ? "Recalculating..." : "Recalculate Quote"}
              </button>
            </div>
          ) : null}

          <div className="flex items-center justify-between border-t border-slate-100 pt-4">
            <button type="button" className="btn-secondary" onClick={handleBack}>
              &larr; Modify Package Specs
            </button>
            <button type="button" className="btn-primary" onClick={handleNext} disabled={quoteExpired}>
              Proceed to Confirmation &rarr;
            </button>
          </div>
        </div>
      )}

      {/* Step 5: Review & Confirm */}
      {step === 5 && quote && (
        <div className="card space-y-5">
          <div>
            <h2 className="text-lg font-bold text-slate-900">Step 5: Review &amp; Confirm Booking</h2>
            <p className="text-sm text-slate-600">
              Please double check all booking details before publishing the delivery request.
            </p>
          </div>

          <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
            {/* Pickup Summary */}
            <div className="space-y-1 rounded-xl border border-slate-200 bg-slate-50/50 p-4 text-sm">
              <span className="block text-xs font-bold uppercase tracking-wider text-slate-800">Pickup Address</span>
              <p className="font-medium text-slate-900">{form.pickupLine1}</p>
              {form.pickupLine2 && <p className="text-slate-600">{form.pickupLine2}</p>}
              <p className="text-slate-600">
                {form.pickupCity}, {form.pickupDistrict} {form.pickupPostalCode}
              </p>
              {form.fromPoint && (
                <p className="text-xs text-slate-500">
                  Pinned coordinates: {form.fromPoint.latitude.toFixed(5)}, {form.fromPoint.longitude.toFixed(5)}
                </p>
              )}
            </div>

            {/* Dropoff Summary */}
            <div className="space-y-1 rounded-xl border border-slate-200 bg-slate-50/50 p-4 text-sm">
              <span className="block text-xs font-bold uppercase tracking-wider text-slate-800">
                Recipient &amp; Drop-off
              </span>
              <p className="font-medium text-slate-900">{form.recipientName}</p>
              <p className="font-mono text-xs text-slate-600">{form.recipientPhone}</p>
              {form.recipientEmail && <p className="text-xs text-slate-600">{form.recipientEmail}</p>}
              <p className="mt-1 text-slate-600">
                {form.dropoffLine1}, {form.dropoffLine2 ? `${form.dropoffLine2}, ` : ""}
                {form.dropoffCity}, {form.dropoffDistrict} {form.dropoffPostalCode}
              </p>
              {form.toPoint && (
                <p className="text-xs text-slate-500">
                  Pinned coordinates: {form.toPoint.latitude.toFixed(5)}, {form.toPoint.longitude.toFixed(5)}
                </p>
              )}
              {form.notes && <p className="mt-1 text-xs text-slate-500">Note: {form.notes}</p>}
            </div>
          </div>

          {/* Package & Fee Summary */}
          <div className="space-y-3 rounded-xl border border-slate-200 p-4 text-sm">
            <div className="flex flex-wrap items-center justify-between gap-4">
              <div>
                <span className="block text-xs uppercase tracking-wider text-slate-500">Specs</span>
                <p className="font-semibold text-slate-800">
                  {quote.chargeableWeightKg ?? form.weightKg} kg &middot; Size {quote.sizeClass} &middot;{" "}
                  {serviceLevelLabel(quote.serviceLevel)}
                  {quote.serviceLevel === "SAME_DAY" && quote.vehicleType ? ` (${quote.vehicleType})` : ""}
                </p>
              </div>
              <div>
                <span className="block text-xs uppercase tracking-wider text-slate-500">Confirmed Fee</span>
                <p className="text-xl font-bold text-brand-900">{formatMoney(quote.fee.amount, quote.fee.currency)}</p>
              </div>
              <div>
                <span className="block text-xs uppercase tracking-wider text-slate-500">Quote Expiry</span>
                <p className={`font-semibold ${quoteExpired ? "text-rose-700" : "text-amber-700"}`}>{timeRemaining}</p>
              </div>
            </div>

            {form.lengthCm || form.codAmount || form.declaredValue || form.specialHandling.length > 0 ? (
              <div className="flex flex-wrap gap-2 border-t border-slate-100 pt-3 text-xs text-slate-600">
                {form.lengthCm && form.widthCm && form.heightCm ? (
                  <span className="status-pill-neutral">
                    Dimensions: {form.lengthCm} x {form.widthCm} x {form.heightCm} cm
                  </span>
                ) : null}
                {form.codAmount && Number(form.codAmount) > 0 ? (
                  <span className="status-pill-neutral">COD: {formatMoney(Number(form.codAmount))}</span>
                ) : null}
                {form.declaredValue && Number(form.declaredValue) > 0 ? (
                  <span className="status-pill-neutral">Cover: {formatMoney(Number(form.declaredValue))}</span>
                ) : null}
                {form.specialHandling.map((code) => (
                  <span key={code} className="status-pill-neutral">
                    {handlingLabelMap.get(code) ?? code}
                  </span>
                ))}
              </div>
            ) : null}
          </div>

          {bookingError && (
            <div className="rounded-lg border border-rose-200 bg-rose-50 p-4 text-sm text-rose-800">
              <p className="font-semibold">Booking could not be completed</p>
              <p>{bookingError}</p>
              <button
                type="button"
                className="mt-2 text-xs font-bold text-rose-900 underline"
                onClick={() => {
                  setStep(3);
                  setBookingError(null);
                }}
              >
                Re-calculate fresh fee quote &rarr;
              </button>
            </div>
          )}

          <div className="flex items-center justify-between border-t border-slate-100 pt-4">
            <button type="button" className="btn-secondary" onClick={handleBack} disabled={submitting}>
              &larr; Back to Quote
            </button>
            <button
              type="button"
              className="btn-primary"
              onClick={handleConfirmBooking}
              disabled={submitting || quoteExpired}
            >
              {submitting ? "Booking Delivery..." : "Confirm & Book Delivery"}
            </button>
          </div>
        </div>
      )}

      {/* Step 6: Success with Tracking Number */}
      {step === 6 && bookedParcel && (
        <div className="card space-y-6 py-8 text-center">
          <div className="mx-auto flex h-14 w-14 items-center justify-center rounded-full bg-emerald-100 text-emerald-700">
            <svg
              className="h-8 w-8"
              fill="none"
              viewBox="0 0 24 24"
              stroke="currentColor"
              strokeWidth={2.5}
              aria-hidden="true"
            >
              <path strokeLinecap="round" strokeLinejoin="round" d="M5 13l4 4L19 7" />
            </svg>
          </div>

          <div className="space-y-2">
            <h2 className="text-2xl font-extrabold text-slate-900">Delivery Successfully Booked!</h2>
            <p className="mx-auto max-w-md text-sm text-slate-600">
              Your parcel booking has been successfully confirmed and scheduled for courier collection.
            </p>
          </div>

          {/* Tracking Number Display */}
          <div className="mx-auto max-w-md space-y-3 rounded-2xl border border-brand-200 bg-brand-50/60 p-6">
            <span className="block text-xs font-semibold uppercase tracking-wider text-brand-800">
              Your Tracking Number
            </span>
            <div className="flex items-center justify-center gap-3">
              <span className="select-all font-mono text-2xl font-extrabold tracking-wider text-brand-900 sm:text-3xl">
                {bookedParcel.trackingNumber}
              </span>
              <button
                type="button"
                onClick={() => copyTrackingNumber(bookedParcel.trackingNumber)}
                className="btn-secondary px-3 py-1.5 text-xs"
                title="Copy tracking number"
              >
                {copied ? "Copied!" : "Copy"}
              </button>
            </div>
            <p className="text-xs text-slate-500">Keep this tracking number safe to track your parcel live online.</p>
          </div>

          {/* Action Links */}
          <div className="flex flex-wrap items-center justify-center gap-4 pt-4">
            <Link href={`/track/${bookedParcel.trackingNumber}`} className="btn-primary">
              Track Parcel Live &rarr;
            </Link>
            <Link href={`/customer/parcels/${bookedParcel.id}`} className="btn-secondary">
              View Parcel Details
            </Link>
            <button
              type="button"
              onClick={() => {
                setForm(buildInitialForm());
                setQuote(null);
                setBookedParcel(null);
                setStep(1);
              }}
              className="px-3 py-2 text-sm font-medium text-slate-600 hover:text-slate-800"
            >
              Book Another Parcel
            </button>
          </div>
        </div>
      )}
    </div>
  );
}

function BookingMapPanel({
  id,
  title,
  purpose,
  tileUrl,
  attribution,
  point,
  district,
  isMaximized,
  onMaximizeChange,
  onClose,
  onPick,
}: {
  id: string;
  title: string;
  purpose: "collection" | "delivery";
  tileUrl: string;
  attribution: string;
  point?: GeoPick;
  district: string;
  isMaximized: boolean;
  onMaximizeChange: (value: boolean) => void;
  onClose: () => void;
  onPick: (point: GeoPick, located: LocatedPoint) => void;
}) {
  if (isMaximized) {
    return (
      <div
        className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/60 p-3 backdrop-blur-xs sm:p-6"
        role="dialog"
        aria-modal="true"
        aria-labelledby={`${id}-modal-title`}
      >
        <div className="flex h-[92vh] w-full max-w-5xl flex-col rounded-2xl bg-white p-4 shadow-2xl ring-1 ring-slate-900/10 sm:p-6">
          <div className="flex flex-wrap items-center justify-between gap-3 border-b border-slate-200 pb-3">
            <div className="flex items-center gap-2">
              <span className="flex h-7 w-7 items-center justify-center rounded-lg bg-brand-50 text-brand-700">
                <MapPinIcon className="h-4 w-4" />
              </span>
              <div>
                <h3 id={`${id}-modal-title`} className="text-sm font-bold text-slate-900">
                  {title}
                </h3>
                <p className="text-xs text-slate-500">
                  {district ? `Detected district: ${district}` : "Tap anywhere in Sri Lanka to auto-detect district."}
                </p>
              </div>
            </div>

            <div className="flex items-center gap-2">
              <button
                type="button"
                onClick={() => onMaximizeChange(false)}
                className="inline-flex items-center gap-1.5 rounded-lg border border-slate-200 bg-white px-3 py-1.5 text-xs font-semibold text-slate-700 shadow-xs hover:bg-slate-50 transition"
              >
                <span>Minimize</span>
              </button>
              <button
                type="button"
                onClick={onClose}
                className="inline-flex items-center gap-1 rounded-lg bg-brand-600 px-3 py-1.5 text-xs font-semibold text-white shadow-xs hover:bg-brand-700 transition"
              >
                Done
              </button>
            </div>
          </div>

          <div className="mt-3 flex-1 overflow-hidden">
            <LocationPicker
              id={`${id}Max`}
              purpose={purpose}
              tileUrl={tileUrl}
              attribution={attribution}
              point={point}
              onPick={onPick}
              heightClass="h-full min-h-[50vh]"
            />
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="space-y-3 rounded-xl border border-slate-200 bg-slate-50/70 p-4">
      <div className="flex flex-wrap items-center justify-between gap-2 border-b border-slate-200 pb-2">
        <span className="text-xs font-semibold text-slate-700">{title}</span>
        <div className="flex items-center gap-2">
          <button
            type="button"
            onClick={() => onMaximizeChange(true)}
            className="inline-flex items-center gap-1.5 rounded-lg border border-slate-200 bg-white px-2.5 py-1 text-xs font-medium text-slate-700 shadow-2xs hover:bg-slate-100 transition"
            aria-label="Maximize"
          >
            <span>Maximize</span>
          </button>
          <button type="button" onClick={onClose} className="text-xs text-slate-500 hover:text-slate-800">
            Close map
          </button>
        </div>
      </div>
      <LocationPicker
        id={id}
        purpose={purpose}
        tileUrl={tileUrl}
        attribution={attribution}
        point={point}
        onPick={onPick}
        heightClass="h-56"
      />
    </div>
  );
}

function MapPinIcon({ className = "h-4 w-4" }: { className?: string }) {
  return (
    <svg className={className} fill="none" viewBox="0 0 24 24" stroke="currentColor" aria-hidden="true">
      <path
        strokeLinecap="round"
        strokeLinejoin="round"
        strokeWidth={2}
        d="M17.657 16.657L13.414 20.9a1.998 1.998 0 01-2.827 0l-4.244-4.243a8 8 0 1111.314 0z"
      />
      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 11a3 3 0 11-6 0 3 3 0 016 0z" />
    </svg>
  );
}
