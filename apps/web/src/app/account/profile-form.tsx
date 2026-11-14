"use client";

import { useActionState, useState } from "react";
import { FEEDBACK_MOTION, PRESS } from "@/lib/motion";
import { formatDate, formatPhone, MAX_NAME_LENGTH, type Profile, type ProfileFormState } from "@/lib/profile";
import { updateProfile } from "./actions";

const INITIAL: ProfileFormState = { status: "idle" };

export function ProfileForm({ initial }: { initial: Profile }) {
  const [state, formAction, pending] = useActionState(updateProfile, INITIAL);
  const profile = state.profile ?? initial;

  // Controlled inputs: React resets uncontrolled fields after every action, which would wipe what
  // the user typed whenever validation fails.
  const [fullName, setFullName] = useState(initial.fullName);
  const [phone, setPhone] = useState(initial.phone ?? "");
  const [syncedSave, setSyncedSave] = useState<number | undefined>(undefined);
  if (state.savedAt !== syncedSave) {
    // After a save, show exactly what was stored (the phone comes back in +94 form).
    setSyncedSave(state.savedAt);
    if (state.profile) {
      setFullName(state.profile.fullName);
      setPhone(state.profile.phone ?? "");
    }
  }

  const unchanged = fullName.trim() === profile.fullName && phone.replace(/\s+/g, "") === (profile.phone ?? "");
  // "Saved" only describes the form while it still matches what was saved; errors stay until the next try.
  const showResult = !pending && state.status !== "idle" && !!state.message && (state.status === "error" || unchanged);
  const nameError = state.fieldErrors?.fullName;
  const phoneError = state.fieldErrors?.phone;

  return (
    <div className="space-y-6">
      <section className="card space-y-4 text-sm" aria-labelledby="account-heading">
        <h2 id="account-heading" className="font-semibold">Account</h2>
        <dl className="grid gap-x-6 gap-y-3 sm:grid-cols-[10rem_1fr]">
          <dt className="text-slate-500">E-mail</dt>
          <dd className="break-all">
            {profile.email}
            <span className="block text-xs text-slate-500">Managed by your sign-in account</span>
          </dd>
          <dt className="text-slate-500">Roles</dt>
          <dd className="flex flex-wrap gap-1">
            {profile.roles.map((role) => (
              <span key={role} className="rounded-full bg-brand-50 px-2 py-0.5 text-xs font-medium text-brand-700">
                {role}
              </span>
            ))}
          </dd>
          <dt className="text-slate-500">Phone</dt>
          <dd>{formatPhone(profile.phone)}</dd>
          <dt className="text-slate-500">Member since</dt>
          <dd>{formatDate(profile.createdAt)}</dd>
        </dl>
      </section>

      <form action={formAction} className="card space-y-5" noValidate aria-labelledby="edit-heading">
        <h2 id="edit-heading" className="font-semibold">Edit details</h2>

        <div className="space-y-1.5">
          <label htmlFor="fullName" className="text-sm font-medium">Full name</label>
          <input
            id="fullName"
            name="fullName"
            value={fullName}
            onChange={(event) => setFullName(event.target.value)}
            maxLength={MAX_NAME_LENGTH}
            autoComplete="name"
            required
            aria-invalid={nameError ? true : undefined}
            aria-describedby={nameError ? "fullName-error" : undefined}
            className="input aria-[invalid=true]:border-red-400 aria-[invalid=true]:focus:ring-red-100"
          />
          {nameError && (
            <p id="fullName-error" key={`name-${state.message}-${nameError}`} className={`text-sm text-red-700 ${FEEDBACK_MOTION}`}>
              {nameError}
            </p>
          )}
        </div>

        <div className="space-y-1.5">
          <label htmlFor="phone" className="text-sm font-medium">
            Mobile number <span className="font-normal text-slate-500">(optional)</span>
          </label>
          <input
            id="phone"
            name="phone"
            type="tel"
            inputMode="tel"
            value={phone}
            onChange={(event) => setPhone(event.target.value)}
            placeholder="0771234567"
            autoComplete="tel"
            aria-invalid={phoneError ? true : undefined}
            aria-describedby={phoneError ? "phone-error phone-hint" : "phone-hint"}
            className="input aria-[invalid=true]:border-red-400 aria-[invalid=true]:focus:ring-red-100"
          />
          <p id="phone-hint" className="text-xs text-slate-500">
            Used for delivery updates. Leave it empty to remove your number.
          </p>
          {phoneError && (
            <p id="phone-error" key={`phone-${state.message}-${phoneError}`} className={`text-sm text-red-700 ${FEEDBACK_MOTION}`}>
              {phoneError}
            </p>
          )}
        </div>

        <div className="flex flex-wrap items-center gap-3">
          <button
            type="submit"
            className={`btn-primary ${PRESS}`}
            disabled={pending || unchanged}
          >
            {pending ? "Saving..." : "Save changes"}
          </button>
          <p role="status" aria-live="polite" className="text-sm">
            {showResult && (
              <span
                key={state.savedAt ?? state.message}
                className={`inline-block ${state.status === "saved" ? "text-emerald-700" : "text-red-700"} ${FEEDBACK_MOTION}`}
              >
                {state.message}
              </span>
            )}
          </p>
        </div>
      </form>
    </div>
  );
}
