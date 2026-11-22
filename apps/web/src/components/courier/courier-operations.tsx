"use client";

import Link from "next/link";
import { useState } from "react";
import { dispatchRequest, type DispatchParcel } from "@/lib/dispatch";

const nextStatus: Record<string, string | undefined> = {
  CREATED: "PICKED_UP",
  PICKED_UP: "IN_TRANSIT",
  IN_TRANSIT: "OUT_FOR_DELIVERY",
};

const failureReasons = ["RECIPIENT_UNAVAILABLE", "WRONG_ADDRESS", "REFUSED", "DAMAGED", "OTHER"] as const;
type ProofState = { proofId: string; fileName: string };

export function CourierOperations({ parcels, onRefresh }: { parcels: DispatchParcel[]; onRefresh: () => void }) {
  const [busy, setBusy] = useState<string | null>(null);
  const [message, setMessage] = useState<{ id: string; text: string; error?: boolean } | null>(null);
  const [proofs, setProofs] = useState<Record<string, ProofState>>({});
  const [failure, setFailure] = useState<Record<string, string>>({});

  async function update(parcel: DispatchParcel, body: Record<string, unknown>) {
    setBusy(parcel.parcelId);
    setMessage(null);
    try {
      await dispatchRequest(`courier/parcels/${parcel.parcelId}/status`, {
        method: "PATCH", headers: { "Content-Type": "application/json" }, body: JSON.stringify(body),
      });
      setMessage({ id: parcel.parcelId, text: "Parcel status updated." });
      onRefresh();
    } catch (error) {
      setMessage({ id: parcel.parcelId, text: error instanceof Error ? error.message : "Status update failed", error: true });
    } finally { setBusy(null); }
  }

  async function uploadProof(parcel: DispatchParcel, file: File) {
    if (file.size === 0 || file.size > 5 * 1024 * 1024 || !["image/jpeg", "image/png"].includes(file.type)) {
      setMessage({ id: parcel.parcelId, text: "Choose a JPEG or PNG image no larger than 5 MB.", error: true });
      return;
    }
    setBusy(parcel.parcelId);
    try {
      const response = await dispatchRequest<{ proofId: string; uploadUrl: string }>(`courier/parcels/${parcel.parcelId}/proof-upload-url`, {
        method: "POST", headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ contentType: file.type, sizeBytes: file.size }),
      });
      const upload = await fetch(response.uploadUrl, { method: "PUT", headers: { "Content-Type": file.type }, body: file });
      if (!upload.ok) throw new Error("The proof photo could not be uploaded");
      setProofs(current => ({ ...current, [parcel.parcelId]: { proofId: response.proofId, fileName: file.name } }));
      setMessage({ id: parcel.parcelId, text: `${file.name} uploaded. You can now mark it delivered.` });
    } catch (error) {
      setMessage({ id: parcel.parcelId, text: error instanceof Error ? error.message : "Proof upload failed", error: true });
    } finally { setBusy(null); }
  }

  return <ol className="mt-4 grid gap-4 md:grid-cols-2">
    {parcels.map((parcel, index) => {
      const isBusy = busy === parcel.parcelId;
      const proof = proofs[parcel.parcelId];
      const advanceTo = nextStatus[parcel.status];
      return <li key={parcel.parcelId} className="rounded-xl bg-slate-50 p-4 ring-1 ring-slate-200">
        <div className="flex justify-between gap-2">
          <Link className="font-mono font-semibold text-brand-700 hover:underline" href={`/track/${encodeURIComponent(parcel.trackingNumber)}`}>{index + 1}. {parcel.trackingNumber}</Link>
          <span className="text-xs text-slate-500">{parcel.sizeClass} · {parcel.zone}</span>
        </div>
        <p className="mt-3 text-sm">{parcel.pickupCity} &rarr; {parcel.dropoffCity}</p>
        <p className="mt-2 text-sm font-semibold text-slate-700">{parcel.status.replaceAll("_", " ")}</p>
        {message?.id === parcel.parcelId && <p role={message.error ? "alert" : "status"} className={`mt-3 text-sm ${message.error ? "text-red-700" : "text-emerald-700"}`}>{message.text}</p>}
        <div className="mt-4 flex flex-wrap gap-2">
          {advanceTo && <button className="btn-primary" disabled={isBusy} onClick={() => void update(parcel, { toStatus: advanceTo })}>{isBusy ? "Updating..." : `Mark ${advanceTo.replaceAll("_", " ")}`}</button>}
          {parcel.status === "OUT_FOR_DELIVERY" && <>
            <label className="btn-secondary cursor-pointer">{proof ? "Replace photo" : "Upload proof photo"}<input className="sr-only" type="file" accept="image/jpeg,image/png" disabled={isBusy} onChange={event => { const file = event.target.files?.[0]; if (file) void uploadProof(parcel, file); event.currentTarget.value = ""; }} /></label>
            <button className="btn-primary" disabled={isBusy || !proof} onClick={() => void update(parcel, { toStatus: "DELIVERED", proof: { type: "PHOTO", proofId: proof?.proofId } })}>Mark delivered</button>
          </>}
          {parcel.status === "OUT_FOR_DELIVERY" && <>
            <select aria-label={`Failure reason for ${parcel.trackingNumber}`} className="rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm" value={failure[parcel.parcelId] ?? ""} onChange={event => setFailure(current => ({ ...current, [parcel.parcelId]: event.target.value }))}>
              <option value="">Failure reason</option>
              {failureReasons.map(reason => <option key={reason} value={reason}>{reason.replaceAll("_", " ")}</option>)}
            </select>
            <button className="btn-secondary" disabled={isBusy} onClick={() => void update(parcel, { toStatus: "FAILED", failureReason: failure[parcel.parcelId] })}>Mark failed</button>
          </>}
        </div>
      </li>;
    })}
  </ol>;
}
