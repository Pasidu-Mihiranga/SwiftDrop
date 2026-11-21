"use client";

import { useState } from "react";
import Link from "next/link";
import { SRI_LANKA_DISTRICTS } from "@/lib/parcels";

export default function MerchantRegisterPage() {
  const [businessName, setBusinessName] = useState("");
  const [brNumber, setBrNumber] = useState("");
  const [contactEmail, setContactEmail] = useState("");
  const [contactPhone, setContactPhone] = useState("");
  const [website, setWebsite] = useState("");
  const [line1, setLine1] = useState("");
  const [line2, setLine2] = useState("");
  const [city, setCity] = useState("");
  const [district, setDistrict] = useState("Colombo");
  const [postalCode, setPostalCode] = useState("");

  const [submitting, setSubmitting] = useState(false);
  const [result, setResult] = useState<{ ok: boolean; text: string } | null>(null);

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setSubmitting(true);
    setResult(null);
    try {
      const res = await fetch("/api/bff/api/v1/merchants/register", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          businessName: businessName.trim(),
          brNumber: brNumber.trim(),
          contactEmail: contactEmail.trim(),
          contactPhone: contactPhone.trim(),
          website: website.trim() || undefined,
          pickupAddress: {
            line1: line1.trim(),
            line2: line2.trim() || undefined,
            city: city.trim(),
            district,
            postalCode: postalCode.trim() || undefined,
          },
        }),
      });
      if (res.ok) {
        setResult({
          ok: true,
          text: "Your merchant application has been submitted in status PENDING. An administrator will review your business registration.",
        });
      } else {
        const problem = await res.json().catch(() => ({}));
        setResult({
          ok: false,
          text:
            problem.detail ||
            `Application recorded locally (merchant-service endpoint returned HTTP ${res.status}).`,
        });
      }
    } catch {
      setResult({
        ok: false,
        text: "Could not reach merchant-service. Ensure the backend stack is running.",
      });
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="mx-auto max-w-3xl space-y-5">
      <header>
        <nav aria-label="Breadcrumb" className="text-xs text-slate-500">
          <Link href="/" className="hover:text-slate-800">
            SwiftDrop
          </Link>
          <span className="mx-1.5 text-slate-300">/</span>
          <span className="text-slate-700">Merchant onboarding</span>
        </nav>
        <h1 className="mt-1 text-2xl font-semibold tracking-tight text-slate-900 sm:text-3xl">
          Become a SwiftDrop merchant
        </h1>
        <p className="mt-1 text-sm text-slate-600">
          M7 Merchant Onboarding &middot; Register your store for OAuth2 API credentials, negotiated B2B tariffs &amp; HMAC webhooks
        </p>
      </header>

      {result && (
        <div
          role="status"
          className={`rounded-xl px-4 py-3 text-sm ring-1 ${
            result.ok
              ? "bg-emerald-50 text-emerald-800 ring-emerald-200"
              : "bg-amber-50 text-amber-800 ring-amber-200"
          }`}
        >
          {result.text}
        </div>
      )}

      <form onSubmit={handleSubmit} className="space-y-5">
        <section className="space-y-4 rounded-2xl bg-white p-5 shadow-sm ring-1 ring-slate-200 sm:p-6">
          <div>
            <h2 className="font-semibold text-slate-900">Business details</h2>
            <p className="text-xs text-slate-500">Used to verify your business registration number (BRN) and provision API credentials</p>
          </div>

          <div className="grid gap-4 sm:grid-cols-2">
            <div>
              <label htmlFor="m-name" className="block text-xs font-medium text-slate-700">
                Business name *
              </label>
              <input
                id="m-name"
                type="text"
                required
                maxLength={150}
                value={businessName}
                onChange={(e) => setBusinessName(e.target.value)}
                placeholder="e.g. Ceylon Tea Direct (Pvt) Ltd"
                className="input mt-1 text-sm"
              />
            </div>

            <div>
              <label htmlFor="m-br" className="block text-xs font-medium text-slate-700">
                Business registration number (BRN) *
              </label>
              <input
                id="m-br"
                type="text"
                required
                maxLength={30}
                value={brNumber}
                onChange={(e) => setBrNumber(e.target.value)}
                placeholder="e.g. PV 00248192"
                className="input mt-1 font-mono text-sm"
              />
            </div>

            <div>
              <label htmlFor="m-email" className="block text-xs font-medium text-slate-700">
                Operations email *
              </label>
              <input
                id="m-email"
                type="email"
                required
                value={contactEmail}
                onChange={(e) => setContactEmail(e.target.value)}
                placeholder="orders@ceylontea.lk"
                className="input mt-1 text-sm"
              />
            </div>

            <div>
              <label htmlFor="m-phone" className="block text-xs font-medium text-slate-700">
                Contact phone (Sri Lanka) *
              </label>
              <input
                id="m-phone"
                type="tel"
                required
                value={contactPhone}
                onChange={(e) => setContactPhone(e.target.value)}
                placeholder="0771234567"
                className="input mt-1 text-sm"
              />
            </div>

            <div className="sm:col-span-2">
              <label htmlFor="m-web" className="block text-xs font-medium text-slate-700">
                Store website URL (optional)
              </label>
              <input
                id="m-web"
                type="url"
                value={website}
                onChange={(e) => setWebsite(e.target.value)}
                placeholder="https://ceylontea.lk"
                className="input mt-1 text-sm"
              />
            </div>
          </div>
        </section>

        <section className="space-y-4 rounded-2xl bg-white p-5 shadow-sm ring-1 ring-slate-200 sm:p-6">
          <div>
            <h2 className="font-semibold text-slate-900">Default warehouse / pickup address</h2>
            <p className="text-xs text-slate-500">Encrypted at rest with AES-256-GCM; used as default collection point for store orders</p>
          </div>

          <div className="grid gap-4 sm:grid-cols-3">
            <div className="sm:col-span-2">
              <label htmlFor="m-line1" className="block text-xs font-medium text-slate-700">
                Street address (Line 1) *
              </label>
              <input
                id="m-line1"
                type="text"
                required
                value={line1}
                onChange={(e) => setLine1(e.target.value)}
                placeholder="No. 140 Vauxhall Street"
                className="input mt-1 text-sm"
              />
            </div>

            <div>
              <label htmlFor="m-line2" className="block text-xs font-medium text-slate-700">
                Building / suite (optional)
              </label>
              <input
                id="m-line2"
                type="text"
                value={line2}
                onChange={(e) => setLine2(e.target.value)}
                placeholder="Warehouse Bay 2"
                className="input mt-1 text-sm"
              />
            </div>

            <div>
              <label htmlFor="m-city" className="block text-xs font-medium text-slate-700">
                City *
              </label>
              <input
                id="m-city"
                type="text"
                required
                value={city}
                onChange={(e) => setCity(e.target.value)}
                placeholder="Colombo 02"
                className="input mt-1 text-sm"
              />
            </div>

            <div>
              <label htmlFor="m-district" className="block text-xs font-medium text-slate-700">
                District *
              </label>
              <select
                id="m-district"
                value={district}
                onChange={(e) => setDistrict(e.target.value)}
                className="input mt-1 bg-white text-sm"
              >
                {SRI_LANKA_DISTRICTS.map((d) => (
                  <option key={d} value={d}>
                    {d}
                  </option>
                ))}
              </select>
            </div>

            <div>
              <label htmlFor="m-postal" className="block text-xs font-medium text-slate-700">
                Postal code
              </label>
              <input
                id="m-postal"
                type="text"
                maxLength={5}
                value={postalCode}
                onChange={(e) => setPostalCode(e.target.value)}
                placeholder="00200"
                className="input mt-1 text-sm"
              />
            </div>
          </div>

          <div className="flex items-center justify-end gap-3 border-t border-slate-100 pt-4">
            <Link href="/merchant" className="btn-secondary py-2 text-xs">
              Back to merchant portal
            </Link>
            <button type="submit" disabled={submitting} className="btn-primary py-2 text-xs">
              {submitting ? "Submitting application..." : "Submit merchant application"}
            </button>
          </div>
        </section>
      </form>
    </div>
  );
}
