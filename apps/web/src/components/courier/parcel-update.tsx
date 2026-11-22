"use client";

import Link from "next/link";
import { useEffect, useId, useState } from "react";
import {
  ACTION_LABELS,
  CourierError,
  type CourierParcel,
  FAILURE_REASONS,
  type FailureReason,
  LIFECYCLE,
  type ParcelStatus,
  STATUS_LABELS,
  loadParcel,
  nextSteps,
  proofFileProblem,
  requestProofUpload,
  statusUpdateBody,
  stepState,
  updateStatus,
  uploadProof,
} from "@/lib/courier";

type Panel = "none" | "DELIVERED" | "FAILED";

/**
 * The courier's screen for one parcel (M4), built for a phone held in one hand: the parcel and
 * recipient at the top, the lifecycle as a stepper, and one large button per step the courier may
 * take now. Simple steps update at once and roll back with a clear message if the server refuses;
 * a delivery asks for proof and a failure for its reason first.
 */
export function ParcelUpdate({ parcelId }: { parcelId: string }) {
  const [parcel, setParcel] = useState<CourierParcel | null>(null);
  const [loadError, setLoadError] = useState("");
  const [error, setError] = useState("");
  const [saving, setSaving] = useState(false);
  const [panel, setPanel] = useState<Panel>("none");

  useEffect(() => {
    let active = true;
    loadParcel(parcelId)
      .then((found) => active && setParcel(found))
      .catch((e: unknown) => active && setLoadError(e instanceof CourierError ? e.message : "Could not load the parcel."));
    return () => {
      active = false;
    };
  }, [parcelId]);

  async function submit(to: ParcelStatus, body: Record<string, unknown>) {
    if (!parcel) return;
    const before = parcel;
    setError("");
    setSaving(true);
    // Optimistic: show the new status straight away; the server's answer replaces it.
    setParcel({ ...parcel, status: to });
    try {
      setParcel(await updateStatus(parcel.id, body));
      setPanel("none");
    } catch (e) {
      setParcel(before);
      setError(e instanceof CourierError ? e.message : "Something went wrong. Please try again.");
    } finally {
      setSaving(false);
    }
  }

  function choose(to: ParcelStatus) {
    if (to === "DELIVERED" || to === "FAILED") {
      setError("");
      setPanel(to);
      return;
    }
    const { body } = statusUpdateBody(to);
    if (body) void submit(to, body);
  }

  if (loadError) {
    return (
      <div className="mx-auto max-w-lg space-y-4">
        <p role="alert" className="notice-error">{loadError}</p>
        <Link href="/courier" className="btn-secondary w-full py-3">Back to the run sheet</Link>
      </div>
    );
  }
  if (!parcel) return <p className="py-10 text-center text-slate-500">Loading parcel...</p>;

  const steps = nextSteps(parcel.status);

  return (
    <div className="mx-auto max-w-lg space-y-5 pb-10">
      <Link href="/courier" className="text-sm font-medium text-brand-700 hover:underline">&larr; Run sheet</Link>

      <section className="card space-y-3 p-5">
        <div className="flex items-start justify-between gap-3">
          <div>
            <p className="text-xs uppercase tracking-wide text-slate-500">Parcel</p>
            <h1 className="font-mono text-xl font-semibold">{parcel.trackingNumber}</h1>
          </div>
          <span className={parcel.status === "FAILED" ? "status-pill-warning" : parcel.status === "DELIVERED" ? "status-pill-success" : "status-pill-neutral"}>
            {STATUS_LABELS[parcel.status]}
          </span>
        </div>
        <dl className="grid gap-2 text-sm">
          <div>
            <dt className="text-slate-500">Deliver to</dt>
            <dd className="font-medium">
              {[parcel.dropoff?.line1, parcel.dropoff?.line2, parcel.dropoff?.city].filter(Boolean).join(", ")}
            </dd>
          </div>
          {parcel.recipient?.name && (
            <div>
              <dt className="text-slate-500">Recipient</dt>
              <dd className="flex flex-wrap items-center gap-3 font-medium">
                {parcel.recipient.name}
                {parcel.recipient.phone && (
                  <a href={`tel:${parcel.recipient.phone}`} className="btn-secondary px-3 py-1.5 text-xs">
                    Call {parcel.recipient.phone}
                  </a>
                )}
              </dd>
            </div>
          )}
          {parcel.notes && (
            <div>
              <dt className="text-slate-500">Notes</dt>
              <dd>{parcel.notes}</dd>
            </div>
          )}
        </dl>
      </section>

      <Stepper status={parcel.status} />

      {error && <p role="alert" className="notice-error">{error}</p>}

      {panel === "none" && (
        steps.length > 0 ? (
          <div className="grid gap-3" aria-label="Next step">
            {steps.map((step) => (
              <button
                key={step}
                type="button"
                disabled={saving}
                onClick={() => choose(step)}
                className={`${step === "FAILED" ? "btn-secondary" : "btn-primary"} w-full py-4 text-base`}
              >
                {ACTION_LABELS[step]}
              </button>
            ))}
          </div>
        ) : (
          <p className="notice-info">
            {parcel.status === "FAILED"
              ? "Dispatch decides when this parcel goes out again."
              : "Nothing more to do for this parcel."}
          </p>
        )
      )}

      {panel === "DELIVERED" && (
        <DeliveryPanel parcelId={parcel.id} saving={saving} onCancel={() => setPanel("none")} onSubmit={(body) => submit("DELIVERED", body)} />
      )}
      {panel === "FAILED" && (
        <FailurePanel saving={saving} onCancel={() => setPanel("none")} onSubmit={(body) => submit("FAILED", body)} />
      )}
    </div>
  );
}

function Stepper({ status }: { status: ParcelStatus }) {
  return (
    <ol aria-label="Progress" className="grid grid-cols-5 gap-1">
      {LIFECYCLE.map((step) => {
        const state = stepState(step, status);
        const failedHere = status === "FAILED" && step === "OUT_FOR_DELIVERY";
        return (
          <li key={step} aria-current={state === "current" ? "step" : undefined} className="space-y-1 text-center">
            <span
              className={`block h-1.5 rounded-full ${
                failedHere ? "bg-amber-500" : state === "done" ? "bg-brand-600" : state === "current" ? "bg-brand-500" : "bg-slate-200"
              }`}
            />
            <span className={`block text-[11px] leading-tight ${state === "todo" ? "text-slate-400" : "font-medium text-slate-700"}`}>
              {STATUS_LABELS[step]}
            </span>
          </li>
        );
      })}
    </ol>
  );
}

function DeliveryPanel({
  parcelId,
  saving,
  onCancel,
  onSubmit,
}: {
  parcelId: string;
  saving: boolean;
  onCancel: () => void;
  onSubmit: (body: Record<string, unknown>) => void;
}) {
  const id = useId();
  const [receivedBy, setReceivedBy] = useState("");
  const [proofId, setProofId] = useState<string | null>(null);
  const [progress, setProgress] = useState<number | null>(null);
  const [message, setMessage] = useState("");

  async function onPhoto(file: File | undefined) {
    if (!file) return;
    const problem = proofFileProblem(file);
    if (problem) {
      setMessage(problem);
      return;
    }
    setMessage("");
    setProofId(null);
    setProgress(0);
    try {
      const upload = await requestProofUpload(parcelId, file);
      await uploadProof(upload.uploadUrl, file, setProgress);
      setProofId(upload.proofId);
    } catch (e) {
      setProgress(null);
      setMessage(e instanceof CourierError ? e.message : "The photo could not be uploaded. Try again.");
    }
  }

  function confirm() {
    const { body, error } = statusUpdateBody("DELIVERED", { delivery: { receivedBy, proofId } });
    if (error || !body) {
      setMessage(error ?? "");
      return;
    }
    onSubmit(body);
  }

  const uploading = progress !== null && proofId === null;

  return (
    <section className="card space-y-4 p-5" aria-labelledby={`${id}-title`}>
      <h2 id={`${id}-title`} className="text-lg font-semibold">Proof of delivery</h2>
      <label className="block space-y-1 text-sm font-medium">
        Received by
        <input
          className="input py-3 text-base"
          value={receivedBy}
          onChange={(e) => setReceivedBy(e.target.value)}
          maxLength={100}
          autoComplete="off"
          placeholder="Name of the person who took the parcel"
        />
      </label>
      <label className="block space-y-1 text-sm font-medium">
        Photo (optional with a name)
        <input
          type="file"
          accept="image/jpeg,image/png"
          capture="environment"
          className="block w-full text-sm"
          onChange={(e) => void onPhoto(e.target.files?.[0])}
        />
      </label>
      {progress !== null && (
        <div className="space-y-1">
          <div role="progressbar" aria-label="Photo upload" aria-valuemin={0} aria-valuemax={100} aria-valuenow={progress} className="h-2 overflow-hidden rounded-full bg-slate-200">
            <div className="h-full bg-brand-600 transition-all" style={{ width: `${progress}%` }} />
          </div>
          <p className="text-xs text-slate-500">{proofId ? "Photo uploaded." : `Uploading ${progress}%`}</p>
        </div>
      )}
      {message && <p role="alert" className="notice-error">{message}</p>}
      <div className="grid gap-2">
        <button type="button" className="btn-primary w-full py-4 text-base" disabled={saving || uploading} onClick={confirm}>
          Confirm delivery
        </button>
        <button type="button" className="btn-secondary w-full py-3" onClick={onCancel} disabled={saving}>
          Back
        </button>
      </div>
    </section>
  );
}

function FailurePanel({
  saving,
  onCancel,
  onSubmit,
}: {
  saving: boolean;
  onCancel: () => void;
  onSubmit: (body: Record<string, unknown>) => void;
}) {
  const id = useId();
  const [reason, setReason] = useState<FailureReason | "">("");
  const [note, setNote] = useState("");
  const [message, setMessage] = useState("");

  function confirm() {
    const { body, error } = statusUpdateBody("FAILED", { failure: { reason, note } });
    if (error || !body) {
      setMessage(error ?? "");
      return;
    }
    onSubmit(body);
  }

  return (
    <section className="card space-y-4 p-5" aria-labelledby={`${id}-title`}>
      <h2 id={`${id}-title`} className="text-lg font-semibold">Why could it not be delivered?</h2>
      <fieldset className="space-y-2">
        <legend className="sr-only">Reason</legend>
        {FAILURE_REASONS.map((option) => (
          <label key={option.value} className="flex items-center gap-3 rounded-lg border border-slate-200 p-3 text-sm">
            <input type="radio" name={`${id}-reason`} value={option.value} checked={reason === option.value} onChange={() => setReason(option.value)} className="h-5 w-5" />
            {option.label}
          </label>
        ))}
      </fieldset>
      <label className="block space-y-1 text-sm font-medium">
        Note
        <textarea className="input py-2" rows={3} maxLength={500} value={note} onChange={(e) => setNote(e.target.value)} />
      </label>
      {message && <p role="alert" className="notice-error">{message}</p>}
      <div className="grid gap-2">
        <button type="button" className="btn-primary w-full py-4 text-base" disabled={saving} onClick={confirm}>
          Report failed delivery
        </button>
        <button type="button" className="btn-secondary w-full py-3" onClick={onCancel} disabled={saving}>
          Back
        </button>
      </div>
    </section>
  );
}
