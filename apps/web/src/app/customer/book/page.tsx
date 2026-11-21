"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import {
  SRI_LANKA_DISTRICTS,
  SIZE_CLASSES,
  SERVICE_LEVELS,
  formatMoney,
  isValidSriLankanPhone,
  sizeClassDescription,
  serviceLevelDescription,
  type SizeClass,
  type ServiceLevel,
  type ParcelResponse,
  type QuoteResult,
} from "@/lib/parcels";
import type { SavedAddress } from "@/lib/addresses";

type Step = 1 | 2 | 3 | 4 | 5 | 6;

interface FormState {
  // Step 1: Pickup
  pickupSavedId: string;
  pickupLine1: string;
  pickupLine2: string;
  pickupCity: string;
  pickupDistrict: string;
  pickupPostalCode: string;

  // Step 2: Dropoff & Recipient
  recipientName: string;
  recipientPhone: string;
  recipientEmail: string;
  dropoffLine1: string;
  dropoffLine2: string;
  dropoffCity: string;
  dropoffDistrict: string;
  dropoffPostalCode: string;
  notes: string;

  // Step 3: Package specs
  weightKg: string;
  sizeClass: SizeClass;
  serviceLevel: ServiceLevel;
  vehicleType: "BIKE" | "THREE_WHEELER" | "VAN";
}

const INITIAL_FORM: FormState = {
  pickupSavedId: "",
  pickupLine1: "",
  pickupLine2: "",
  pickupCity: "",
  pickupDistrict: "Colombo",
  pickupPostalCode: "",

  recipientName: "",
  recipientPhone: "",
  recipientEmail: "",
  dropoffLine1: "",
  dropoffLine2: "",
  dropoffCity: "",
  dropoffDistrict: "Kandy",
  dropoffPostalCode: "",
  notes: "",

  weightKg: "1.5",
  sizeClass: "S",
  serviceLevel: "STANDARD",
  vehicleType: "BIKE",
};

export default function BookDeliveryPage() {
  const [step, setStep] = useState<Step>(1);
  const [form, setForm] = useState<FormState>(INITIAL_FORM);
  const [savedAddresses, setSavedAddresses] = useState<SavedAddress[]>([]);
  const [loadingAddresses, setLoadingAddresses] = useState(true);

  // Quote state
  const [quote, setQuote] = useState<QuoteResult | null>(null);
  const [quoting, setQuoting] = useState(false);
  const [quoteError, setQuoteError] = useState<string | null>(null);
  const [timeRemaining, setTimeRemaining] = useState<string>("");

  // Booking submit state
  const [submitting, setSubmitting] = useState(false);
  const [bookingError, setBookingError] = useState<string | null>(null);
  const [bookedParcel, setBookedParcel] = useState<ParcelResponse | null>(null);
  const [copied, setCopied] = useState(false);

  // Validation errors
  const [errors, setErrors] = useState<Record<string, string>>({});

  // Load saved addresses from user-service via BFF
  useEffect(() => {
    async function loadAddresses() {
      try {
        const res = await fetch("/api/bff/api/v1/users/me/addresses");
        if (res.ok) {
          const data = (await res.json()) as SavedAddress[];
          setSavedAddresses(data);
          const defaultAddr = data.find((a) => a.isDefault);
          if (defaultAddr) {
            setForm((prev) => ({
              ...prev,
              pickupSavedId: defaultAddr.id,
              pickupLine1: defaultAddr.line1,
              pickupLine2: defaultAddr.line2 || "",
              pickupCity: defaultAddr.city,
              pickupDistrict: defaultAddr.district,
              pickupPostalCode: defaultAddr.postalCode || "",
            }));
          }
        }
      } catch {
        // Fallback to manual entry
      } finally {
        setLoadingAddresses(false);
      }
    }
    loadAddresses();
  }, []);

  // Expiry countdown timer for quote
  useEffect(() => {
    if (!quote || step > 5) return;
    const interval = setInterval(() => {
      const remainingMs = new Date(quote.expiresAt).getTime() - Date.now();
      if (remainingMs <= 0) {
        setTimeRemaining("Expired");
      } else {
        const mins = Math.floor(remainingMs / 60000);
        const secs = Math.floor((remainingMs % 60000) / 1000);
        setTimeRemaining(`${mins}m ${secs < 10 ? "0" : ""}${secs}s`);
      }
    }, 1000);
    return () => clearInterval(interval);
  }, [quote, step]);

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
      }));
      setErrors((prev) => ({
        ...prev,
        pickupLine1: "",
        pickupCity: "",
        pickupDistrict: "",
      }));
    }
  }

  function validateStep(s: Step): boolean {
    const errs: Record<string, string> = {};

    if (s === 1) {
      if (!form.pickupLine1.trim()) errs.pickupLine1 = "Street address is required.";
      if (!form.pickupCity.trim()) errs.pickupCity = "City is required.";
      if (!form.pickupDistrict.trim()) errs.pickupDistrict = "District is required.";
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
      if (form.dropoffPostalCode && !/^[0-9]{5}$/.test(form.dropoffPostalCode.trim())) {
        errs.dropoffPostalCode = "Postal code must be 5 digits.";
      }
    }

    if (s === 3) {
      const w = parseFloat(form.weightKg);
      if (isNaN(w) || w <= 0) errs.weightKg = "Weight must be greater than 0 kg.";
      if (w > 50) errs.weightKg = "Maximum allowable weight is 50 kg.";
    }

    setErrors(errs);
    return Object.keys(errs).length === 0;
  }

  async function fetchQuote() {
    setQuoting(true);
    setQuoteError(null);
    try {
      const res = await fetch("/api/bff/api/v1/pricing/quotes", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          fromDistrict: form.pickupDistrict,
          fromCity: form.pickupCity,
          toDistrict: form.dropoffDistrict,
          toCity: form.dropoffCity,
          weightKg: parseFloat(form.weightKg),
          sizeClass: form.sizeClass,
          serviceLevel: form.serviceLevel,
          ...(form.serviceLevel === "SAME_DAY" ? { vehicleType: form.vehicleType } : {}),
        }),
      });

      if (!res.ok) {
        const errorData = await res.json().catch(() => ({}));
        throw new Error(errorData.detail || `Pricing service returned HTTP ${res.status}`);
      }

      const quoteData = (await res.json()) as QuoteResult;
      setQuote(quoteData);
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
    } else if (step === 4) {
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
        <Link href="/customer" className="text-sm text-brand-600 hover:text-brand-700 font-medium">
          &larr; Back to my deliveries
        </Link>
      </div>

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
                      className={`flex h-7 w-7 items-center justify-center rounded-full font-bold text-xs ${
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
                        isCurrent
                          ? "font-bold text-brand-700"
                          : isCompleted
                          ? "text-slate-700"
                          : "text-slate-400"
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
          <div>
            <h2 className="text-lg font-bold text-slate-900">Step 1: Collection Address</h2>
            <p className="text-sm text-slate-600">Where should our courier collect the parcel from?</p>
          </div>

          {/* Saved Addresses Selector */}
          {savedAddresses.length > 0 && (
            <div className="rounded-lg border border-brand-100 bg-brand-50/50 p-4 space-y-2">
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
                  onChange={(e) => setForm({ ...form, pickupDistrict: e.target.value })}
                >
                  {SRI_LANKA_DISTRICTS.map((d) => (
                    <option key={d} value={d}>
                      {d}
                    </option>
                  ))}
                </select>
                {errors.pickupDistrict && <p className="field-error">{errors.pickupDistrict}</p>}
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

          <div className="flex justify-end pt-4 border-t border-slate-100">
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

            <div className="pt-2 border-t border-slate-100">
              <h3 className="text-sm font-semibold text-slate-800 mb-3">Drop-off Street Address</h3>
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
                      onChange={(e) => setForm({ ...form, dropoffDistrict: e.target.value })}
                    >
                      {SRI_LANKA_DISTRICTS.map((d) => (
                        <option key={d} value={d}>
                          {d}
                        </option>
                      ))}
                    </select>
                    {errors.dropoffDistrict && <p className="field-error">{errors.dropoffDistrict}</p>}
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

          <div className="flex items-center justify-between pt-4 border-t border-slate-100">
            <button type="button" className="btn-secondary" onClick={handleBack}>
              &larr; Back to Pickup
            </button>
            <button type="button" className="btn-primary" onClick={handleNext}>
              Next: Package Specs &rarr;
            </button>
          </div>
        </div>
      )}

      {/* Step 3: Package Specs & Speed */}
      {step === 3 && (
        <div className="card space-y-5">
          <div>
            <h2 className="text-lg font-bold text-slate-900">Step 3: Package Specifications</h2>
            <p className="text-sm text-slate-600">Provide weight, size category, and preferred delivery speed.</p>
          </div>

          <div className="space-y-5">
            <div>
              <label htmlFor="weightKg" className="block text-sm font-medium text-slate-700">
                Weight in Kilograms (kg) *
              </label>
              <input
                id="weightKg"
                type="number"
                step="0.1"
                min="0.1"
                max="50"
                className="input mt-1 max-w-xs"
                value={form.weightKg}
                onChange={(e) => setForm({ ...form, weightKg: e.target.value })}
              />
              {errors.weightKg && <p className="field-error">{errors.weightKg}</p>}
            </div>

            <div>
              <label className="block text-sm font-medium text-slate-700 mb-2">
                Size Category *
              </label>
              <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
                {SIZE_CLASSES.map((size) => (
                  <label
                    key={size}
                    className={`flex items-start gap-3 rounded-xl border p-3 cursor-pointer transition-colors ${
                      form.sizeClass === size
                        ? "border-brand-600 bg-brand-50/40 text-brand-900 ring-1 ring-brand-600"
                        : "border-slate-200 bg-white hover:bg-slate-50 text-slate-800"
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
                      <span className="font-bold text-sm block">Class {size}</span>
                      <span className="text-xs text-slate-500">{sizeClassDescription(size)}</span>
                    </div>
                  </label>
                ))}
              </div>
            </div>

            <div>
              <label className="block text-sm font-medium text-slate-700 mb-2">
                Service Level *
              </label>
              <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
                {SERVICE_LEVELS.map((level) => (
                  <label
                    key={level}
                    className={`flex items-start gap-3 rounded-xl border p-3 cursor-pointer transition-colors ${
                      form.serviceLevel === level
                        ? "border-brand-600 bg-brand-50/40 text-brand-900 ring-1 ring-brand-600"
                        : "border-slate-200 bg-white hover:bg-slate-50 text-slate-800"
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
                      <span className="font-bold text-sm block">
                        {level === "STANDARD"
                          ? "Standard Delivery"
                          : level === "EXPRESS"
                          ? "Express Priority"
                          : "Same-Day Courier"}
                      </span>
                      <span className="text-xs text-slate-500">{serviceLevelDescription(level)}</span>
                    </div>
                  </label>
                ))}
              </div>
            </div>

            {form.serviceLevel === "SAME_DAY" && (
              <div>
                <label htmlFor="vehicleType" className="block text-sm font-medium text-slate-700">
                  Same-Day Vehicle Type
                </label>
                <select
                  id="vehicleType"
                  className="input mt-1 max-w-xs"
                  value={form.vehicleType}
                  onChange={(e) =>
                    setForm({ ...form, vehicleType: e.target.value as "BIKE" | "THREE_WHEELER" | "VAN" })
                  }
                >
                  <option value="BIKE">Motorbike (up to 10 kg)</option>
                  <option value="THREE_WHEELER">Three-Wheeler (up to 25 kg)</option>
                  <option value="VAN">Delivery Van (up to 50 kg)</option>
                </select>
              </div>
            )}
          </div>

          {quoteError && (
            <div className="rounded-lg border border-rose-200 bg-rose-50 p-4 text-sm text-rose-800">
              <p className="font-semibold">Unable to fetch quote</p>
              <p>{quoteError}</p>
            </div>
          )}

          <div className="flex items-center justify-between pt-4 border-t border-slate-100">
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
                Official delivery fee for route: {form.pickupDistrict} &rarr; {form.dropoffDistrict}.
              </p>
            </div>
            <div className="rounded-full bg-amber-50 border border-amber-200 px-3 py-1 text-xs font-semibold text-amber-800">
              Valid for: {timeRemaining}
            </div>
          </div>

          {/* Fee Card */}
          <div className="rounded-2xl border border-brand-100 bg-gradient-to-br from-brand-50 to-white p-6 shadow-xs">
            <div className="flex flex-wrap items-baseline justify-between gap-4 border-b border-brand-100 pb-4">
              <div>
                <span className="text-xs font-semibold uppercase tracking-wider text-brand-700">Total Delivery Fee</span>
                <p className="text-3xl font-extrabold text-brand-900">
                  {formatMoney(quote.fee.amount, quote.fee.currency)}
                </p>
              </div>
              <div className="text-right">
                <span className="text-xs text-slate-500">Quote ID</span>
                <p className="font-mono text-xs text-slate-700">{quote.quoteId.slice(0, 18)}...</p>
              </div>
            </div>

            <div className="mt-4 space-y-2 text-sm">
              <span className="font-semibold text-slate-700 block">Fee Breakdown</span>
              <ul className="divide-y divide-slate-100">
                {quote.breakdown.map((item, idx) => (
                  <li key={idx} className="flex justify-between py-1.5 text-slate-600">
                    <span>{item.rule === "BASE" ? "Base Lane Rate" : item.rule === "WEIGHT" ? "Weight Charge" : item.rule === "SIZE" ? "Size Surcharge" : item.rule}</span>
                    <span className="font-medium text-slate-800">{formatMoney(item.amount, quote.fee.currency)}</span>
                  </li>
                ))}
              </ul>
            </div>
          </div>

          <div className="flex items-center justify-between pt-4 border-t border-slate-100">
            <button type="button" className="btn-secondary" onClick={handleBack}>
              &larr; Modify Package Specs
            </button>
            <button type="button" className="btn-primary" onClick={handleNext}>
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
            <div className="rounded-xl border border-slate-200 bg-slate-50/50 p-4 space-y-1 text-sm">
              <span className="font-bold text-slate-800 block text-xs uppercase tracking-wider">Pickup Address</span>
              <p className="text-slate-900 font-medium">{form.pickupLine1}</p>
              {form.pickupLine2 && <p className="text-slate-600">{form.pickupLine2}</p>}
              <p className="text-slate-600">
                {form.pickupCity}, {form.pickupDistrict} {form.pickupPostalCode}
              </p>
            </div>

            {/* Dropoff Summary */}
            <div className="rounded-xl border border-slate-200 bg-slate-50/50 p-4 space-y-1 text-sm">
              <span className="font-bold text-slate-800 block text-xs uppercase tracking-wider">Recipient &amp; Drop-off</span>
              <p className="text-slate-900 font-medium">{form.recipientName}</p>
              <p className="text-slate-600 font-mono text-xs">{form.recipientPhone}</p>
              {form.recipientEmail && <p className="text-slate-600 text-xs">{form.recipientEmail}</p>}
              <p className="text-slate-600 mt-1">
                {form.dropoffLine1}, {form.dropoffCity}, {form.dropoffDistrict}
              </p>
            </div>
          </div>

          {/* Package & Fee Summary */}
          <div className="rounded-xl border border-slate-200 p-4 flex flex-wrap justify-between items-center gap-4 text-sm">
            <div>
              <span className="text-xs text-slate-500 uppercase tracking-wider block">Specs</span>
              <p className="font-semibold text-slate-800">
                {form.weightKg} kg · Size {form.sizeClass} · {form.serviceLevel}
              </p>
            </div>
            <div>
              <span className="text-xs text-slate-500 uppercase tracking-wider block">Confirmed Fee</span>
              <p className="text-xl font-bold text-brand-900">{formatMoney(quote.fee.amount, quote.fee.currency)}</p>
            </div>
            <div>
              <span className="text-xs text-slate-500 uppercase tracking-wider block">Quote Expiry</span>
              <p className="font-semibold text-amber-700">{timeRemaining}</p>
            </div>
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

          <div className="flex items-center justify-between pt-4 border-t border-slate-100">
            <button type="button" className="btn-secondary" onClick={handleBack} disabled={submitting}>
              &larr; Back to Quote
            </button>
            <button
              type="button"
              className="btn-primary"
              onClick={handleConfirmBooking}
              disabled={submitting || timeRemaining === "Expired"}
            >
              {submitting ? "Booking Delivery..." : "Confirm & Book Delivery"}
            </button>
          </div>
        </div>
      )}

      {/* Step 6: Success with Tracking Number */}
      {step === 6 && bookedParcel && (
        <div className="card space-y-6 text-center py-8">
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
            <p className="text-sm text-slate-600 max-w-md mx-auto">
              Your parcel booking has been successfully confirmed and scheduled for courier collection.
            </p>
          </div>

          {/* Tracking Number Display */}
          <div className="mx-auto max-w-md rounded-2xl border border-brand-200 bg-brand-50/60 p-6 space-y-3">
            <span className="text-xs font-semibold uppercase tracking-wider text-brand-800 block">
              Your Tracking Number
            </span>
            <div className="flex items-center justify-center gap-3">
              <span className="font-mono text-2xl sm:text-3xl font-extrabold tracking-wider text-brand-900 select-all">
                {bookedParcel.trackingNumber}
              </span>
              <button
                type="button"
                onClick={() => copyTrackingNumber(bookedParcel.trackingNumber)}
                className="btn-secondary text-xs px-3 py-1.5"
                title="Copy tracking number"
              >
                {copied ? "Copied!" : "Copy"}
              </button>
            </div>
            <p className="text-xs text-slate-500">
              Keep this tracking number safe to track your parcel live online.
            </p>
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
                setForm(INITIAL_FORM);
                setQuote(null);
                setBookedParcel(null);
                setStep(1);
              }}
              className="text-sm text-slate-600 hover:text-slate-800 font-medium px-3 py-2"
            >
              Book Another Parcel
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
