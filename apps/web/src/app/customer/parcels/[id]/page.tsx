"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import {
  formatMoney,
  statusBadgeClass,
  statusLabel,
  sizeClassDescription,
  serviceLevelDescription,
  type ParcelResponse,
  type ParcelStatus,
} from "@/lib/parcels";

export default function ParcelDetailPage() {
  const params = useParams();
  const router = useRouter();
  const id = params?.id as string;

  const [parcel, setParcel] = useState<ParcelResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Cancellation state
  const [showCancelModal, setShowCancelModal] = useState(false);
  const [cancelling, setCancelling] = useState(false);
  const [cancelError, setCancelError] = useState<string | null>(null);
  const [cancelSuccess, setCancelSuccess] = useState(false);

  // Copy tracking number state
  const [copied, setCopied] = useState(false);

  useEffect(() => {
    if (!id) return;

    async function loadParcel() {
      setLoading(true);
      setError(null);
      try {
        const res = await fetch(`/api/bff/api/v1/parcels/${id}`);
        if (res.ok) {
          const data = (await res.json()) as ParcelResponse;
          setParcel(data);
        } else if (res.status === 404) {
          setError("Parcel not found or you do not have permission to view this parcel.");
        } else {
          setError(`Unable to load parcel details (HTTP ${res.status}).`);
        }
      } catch (err: unknown) {
        setError("Network error while loading parcel details.");
      } finally {
        setLoading(false);
      }
    }

    loadParcel();
  }, [id]);

  function copyTrackingNumber(tn: string) {
    navigator.clipboard.writeText(tn);
    setCopied(true);
    setTimeout(() => setCopied(false), 2500);
  }

  async function handleConfirmCancel() {
    if (!parcel) return;
    setCancelling(true);
    setCancelError(null);

    try {
      const res = await fetch(`/api/bff/api/v1/parcels/${parcel.id}/cancel`, {
        method: "POST",
      });

      if (res.ok) {
        const updated = (await res.json()) as ParcelResponse;
        setParcel(updated);
        setCancelSuccess(true);
        setShowCancelModal(false);
      } else {
        const errJson = await res.json().catch(() => ({}));
        if (res.status === 422) {
          setCancelError(errJson.detail || "Parcel can no longer be cancelled as it has already moved past CREATED status.");
        } else {
          setCancelError(errJson.detail || `Cancellation failed with HTTP ${res.status}`);
        }
      }
    } catch {
      setCancelError("Network error while attempting to cancel the parcel.");
    } finally {
      setCancelling(false);
    }
  }

  if (loading) {
    return (
      <div className="mx-auto max-w-4xl py-12 text-center text-slate-500">
        <p className="text-sm">Loading parcel details...</p>
      </div>
    );
  }

  if (error || !parcel) {
    return (
      <div className="mx-auto max-w-xl card space-y-4 py-8 text-center">
        <div className="mx-auto flex h-12 w-12 items-center justify-center rounded-full bg-rose-100 text-rose-700 font-bold">
          !
        </div>
        <h2 className="text-xl font-bold text-slate-900">Parcel Unavailable</h2>
        <p className="text-sm text-slate-600 max-w-md mx-auto">{error || "Unable to display parcel information."}</p>
        <div className="pt-2">
          <Link href="/customer" className="btn-primary text-sm">
            &larr; Back to My Deliveries
          </Link>
        </div>
      </div>
    );
  }

  const isCreated = parcel.status === "CREATED";

  return (
    <div className="mx-auto max-w-4xl space-y-6">
      {/* Navigation & Header */}
      <div className="flex flex-wrap items-center justify-between gap-4">
        <div>
          <Link href="/customer" className="text-xs font-semibold text-brand-600 hover:text-brand-800">
            &larr; Back to my deliveries
          </Link>
          <div className="flex flex-wrap items-center gap-3 mt-1">
            <h1 className="text-2xl font-extrabold text-slate-900 font-mono tracking-wide">
              {parcel.trackingNumber}
            </h1>
            <button
              type="button"
              onClick={() => copyTrackingNumber(parcel.trackingNumber)}
              className="rounded-md border border-slate-200 bg-white px-2 py-0.5 text-xs font-medium text-slate-600 hover:bg-slate-50"
            >
              {copied ? "Copied!" : "Copy"}
            </button>
            <span
              className={`inline-flex items-center rounded-full border px-3 py-1 text-xs font-bold ${statusBadgeClass(
                parcel.status,
              )}`}
            >
              {statusLabel(parcel.status)}
            </span>
          </div>
          <p className="text-xs text-slate-500 mt-0.5">
            Booked on {new Date(parcel.createdAt).toLocaleString("en-LK")}
          </p>
        </div>

        {/* Action Buttons */}
        <div className="flex flex-wrap items-center gap-2">
          <Link href={`/track?q=${parcel.trackingNumber}`} className="btn-secondary text-xs">
            Live Timeline &rarr;
          </Link>
          <a
            href={`/api/bff/api/v1/parcels/${parcel.id}/receipt`}
            target="_blank"
            rel="noopener noreferrer"
            className="btn-secondary text-xs"
          >
            PDF Receipt
          </a>
          {isCreated && (
            <button
              type="button"
              onClick={() => setShowCancelModal(true)}
              className="rounded-lg border border-rose-300 bg-white px-3 py-1.5 text-xs font-semibold text-rose-700 hover:bg-rose-50 transition-colors"
            >
              Cancel Delivery
            </button>
          )}
        </div>
      </div>

      {cancelSuccess && (
        <div className="rounded-xl border border-emerald-200 bg-emerald-50 p-4 text-sm text-emerald-800">
          <p className="font-semibold">Delivery Successfully Cancelled</p>
          <p>This parcel has been cancelled and will not be dispatched for collection.</p>
        </div>
      )}

      {/* Main Grid */}
      <div className="grid grid-cols-1 gap-6 md:grid-cols-2">
        {/* Collection & Delivery Addresses */}
        <div className="card space-y-4">
          <h2 className="text-base font-bold text-slate-900 border-b border-slate-100 pb-2">
            Route &amp; Contact Details
          </h2>

          {/* Pickup */}
          <div className="space-y-1 text-sm">
            <span className="text-xs font-bold uppercase tracking-wider text-slate-500 block">
              Collection (Pickup)
            </span>
            <p className="font-medium text-slate-900">{parcel.pickup.line1}</p>
            {parcel.pickup.line2 && <p className="text-slate-600">{parcel.pickup.line2}</p>}
            <p className="text-slate-600">
              {parcel.pickup.city}, {parcel.pickup.district} {parcel.pickup.postalCode || ""}
            </p>
          </div>

          {/* Dropoff */}
          <div className="space-y-1 text-sm pt-2 border-t border-slate-100">
            <span className="text-xs font-bold uppercase tracking-wider text-slate-500 block">
              Delivery Destination (Drop-off)
            </span>
            <p className="font-medium text-slate-900">{parcel.dropoff.line1}</p>
            {parcel.dropoff.line2 && <p className="text-slate-600">{parcel.dropoff.line2}</p>}
            <p className="text-slate-600">
              {parcel.dropoff.city}, {parcel.dropoff.district} {parcel.dropoff.postalCode || ""}
            </p>
          </div>

          {/* Recipient */}
          <div className="space-y-1 text-sm pt-2 border-t border-slate-100">
            <span className="text-xs font-bold uppercase tracking-wider text-slate-500 block">
              Recipient Contact Details
            </span>
            <p className="font-medium text-slate-900">{parcel.recipient.name}</p>
            <p className="text-slate-700 font-mono text-xs">{parcel.recipient.phone}</p>
            {parcel.recipient.email && <p className="text-slate-500 text-xs">{parcel.recipient.email}</p>}
          </div>

          {/* Notes */}
          {parcel.notes && (
            <div className="space-y-1 text-sm pt-2 border-t border-slate-100">
              <span className="text-xs font-bold uppercase tracking-wider text-slate-500 block">
                Delivery Instructions
              </span>
              <p className="text-slate-700 italic text-xs">{parcel.notes}</p>
            </div>
          )}
        </div>

        {/* Specifications & Fee Summary */}
        <div className="space-y-6">
          <div className="card space-y-4">
            <h2 className="text-base font-bold text-slate-900 border-b border-slate-100 pb-2">
              Package &amp; Fee Details
            </h2>

            <div className="grid grid-cols-2 gap-4 text-sm">
              <div>
                <span className="text-xs text-slate-500 uppercase tracking-wider block">Weight</span>
                <span className="font-bold text-slate-800">{parcel.weightKg} kg</span>
              </div>
              <div>
                <span className="text-xs text-slate-500 uppercase tracking-wider block">Size Class</span>
                <span className="font-bold text-slate-800">Class {parcel.sizeClass}</span>
                <span className="text-xs text-slate-500 block">{sizeClassDescription(parcel.sizeClass)}</span>
              </div>
              <div>
                <span className="text-xs text-slate-500 uppercase tracking-wider block">Service Level</span>
                <span className="font-bold text-slate-800">{parcel.serviceLevel}</span>
                <span className="text-xs text-slate-500 block">{serviceLevelDescription(parcel.serviceLevel)}</span>
              </div>
              <div>
                <span className="text-xs text-slate-500 uppercase tracking-wider block">Total Delivery Fee</span>
                <span className="text-lg font-extrabold text-brand-900 block">
                  {formatMoney(parcel.fee.amount, parcel.fee.currency)}
                </span>
              </div>
            </div>

            {parcel.courierId && (
              <div className="pt-2 border-t border-slate-100 text-sm">
                <span className="text-xs text-slate-500 uppercase tracking-wider block">Assigned Courier</span>
                <span className="font-mono text-xs text-slate-700">{parcel.courierId}</span>
              </div>
            )}
          </div>

          {/* Status History Timeline */}
          <div className="card space-y-4">
            <h2 className="text-base font-bold text-slate-900 border-b border-slate-100 pb-2">
              Status History Timeline
            </h2>

            {parcel.history && parcel.history.length > 0 ? (
              <ol className="relative border-l border-slate-200 ml-3 space-y-4 text-xs">
                {parcel.history.map((entry, idx) => (
                  <li key={idx} className="mb-2 ml-4">
                    <div className="absolute -left-1.5 mt-1.5 h-3 w-3 rounded-full border border-white bg-brand-600" />
                    <time className="mb-1 text-slate-400">
                      {new Date(entry.occurredAt).toLocaleString("en-LK")}
                    </time>
                    <h3 className="font-bold text-slate-900">
                      {entry.fromStatus ? `${entry.fromStatus} \u2192 ` : "Initial: "}
                      <span className="text-brand-700">{entry.toStatus}</span>
                    </h3>
                    <p className="text-slate-500">
                      Updated by: {entry.actorRole === "CUSTOMER" ? "Customer" : entry.actorRole === "COURIER" ? "Assigned Courier" : "SwiftDrop Operations"}
                    </p>
                  </li>
                ))}
              </ol>
            ) : (
              <p className="text-xs text-slate-500">No status transitions recorded yet.</p>
            )}
          </div>
        </div>
      </div>

      {/* Cancel Confirmation Modal */}
      {showCancelModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4">
          <div className="card max-w-md w-full space-y-4 shadow-xl">
            <h3 className="text-lg font-bold text-slate-900">Cancel Delivery Request?</h3>
            <p className="text-sm text-slate-600">
              Are you sure you want to cancel delivery <strong>{parcel.trackingNumber}</strong>? Once cancelled, this parcel will not be picked up.
            </p>

            {cancelError && (
              <div className="rounded-lg border border-rose-200 bg-rose-50 p-3 text-xs text-rose-800">
                {cancelError}
              </div>
            )}

            <div className="flex items-center justify-end gap-3 pt-4 border-t border-slate-100">
              <button
                type="button"
                className="btn-secondary text-xs"
                onClick={() => {
                  setShowCancelModal(false);
                  setCancelError(null);
                }}
                disabled={cancelling}
              >
                No, Keep Delivery
              </button>
              <button
                type="button"
                className="rounded-lg bg-rose-600 px-4 py-2 text-xs font-semibold text-white hover:bg-rose-700 transition-colors"
                onClick={handleConfirmCancel}
                disabled={cancelling}
              >
                {cancelling ? "Cancelling..." : "Yes, Cancel Delivery"}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
