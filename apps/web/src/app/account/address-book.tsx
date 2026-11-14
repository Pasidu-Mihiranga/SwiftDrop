"use client";

import { useActionState, useEffect, useRef, useState, useTransition } from "react";
import { formatAddress, MAX_ADDRESSES, type AddressField, type AddressFormState, type SavedAddress } from "@/lib/addresses";
import { FEEDBACK_MOTION, PRESS } from "@/lib/motion";
import { deleteAddress, makeDefaultAddress, saveAddress } from "./address-actions";

type Mode = { kind: "list" } | { kind: "add" } | { kind: "edit"; id: string };
interface Notice {
  ok: boolean;
  message: string;
  key: number;
}

const LINK_BUTTON = `rounded-md px-2 py-1 text-sm font-medium text-brand-700 transition hover:bg-brand-50 disabled:opacity-50 ${PRESS}`;

/**
 * The address book under "My profile". Saved addresses are rendered by the server (the page re-renders
 * after every change), so this component only holds which form is open and the last result message.
 */
export function AddressBook({ addresses, districts }: { addresses: SavedAddress[]; districts: string[] }) {
  const [mode, setMode] = useState<Mode>({ kind: "list" });
  const [notice, setNotice] = useState<Notice | null>(null);
  const [confirmingId, setConfirmingId] = useState<string | null>(null);
  const [pending, startTransition] = useTransition();
  const full = addresses.length >= MAX_ADDRESSES;

  function run(action: () => Promise<{ ok: boolean; message: string }>) {
    startTransition(async () => {
      const result = await action();
      setNotice({ ...result, key: Date.now() });
      setConfirmingId(null);
    });
  }

  function closeForm(message: string) {
    setMode({ kind: "list" });
    setNotice({ ok: true, message, key: Date.now() });
  }

  return (
    <section className="card space-y-4" aria-labelledby="addresses-heading">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h2 id="addresses-heading" className="font-semibold">Address book</h2>
          <p className="text-sm text-slate-500">
            Saved pickup and drop-off places · {addresses.length} of {MAX_ADDRESSES}
          </p>
        </div>
        {mode.kind !== "add" && (
          <button
            type="button"
            className={`btn-secondary ${PRESS}`}
            onClick={() => {
              setMode({ kind: "add" });
              setNotice(null);
            }}
            disabled={full}
            title={full ? `You can keep up to ${MAX_ADDRESSES} addresses` : undefined}
          >
            Add address
          </button>
        )}
      </div>

      <p role="status" aria-live="polite" className="text-sm">
        {notice && (
          <span key={notice.key} className={`inline-block ${notice.ok ? "text-emerald-700" : "text-red-700"} ${FEEDBACK_MOTION}`}>
            {notice.message}
          </span>
        )}
      </p>

      {mode.kind === "add" && (
        <AddressForm districts={districts} onSaved={closeForm} onCancel={() => setMode({ kind: "list" })} isFirst={addresses.length === 0} />
      )}

      {addresses.length === 0 && mode.kind !== "add" ? (
        <p className="rounded-lg border border-dashed border-slate-300 p-6 text-center text-sm text-slate-500">
          No saved addresses yet. Add one to fill in your bookings faster.
        </p>
      ) : (
        <ul className="divide-y divide-slate-100">
          {addresses.map((address) =>
            mode.kind === "edit" && mode.id === address.id ? (
              <li key={address.id} className="py-4">
                <AddressForm address={address} districts={districts} onSaved={closeForm} onCancel={() => setMode({ kind: "list" })} />
              </li>
            ) : (
              <li key={address.id} className="flex flex-wrap items-start justify-between gap-3 py-4">
                <div className="min-w-0 space-y-1">
                  <p className="flex items-center gap-2 font-medium">
                    {address.label}
                    {address.isDefault && (
                      <span className="rounded-full bg-brand-50 px-2 py-0.5 text-xs font-medium text-brand-700">Default</span>
                    )}
                  </p>
                  <p className="break-words text-sm text-slate-600">{formatAddress(address)}</p>
                </div>
                {confirmingId === address.id ? (
                  <div className="flex items-center gap-2 text-sm" role="group" aria-label={`Delete ${address.label}?`}>
                    <span className="text-slate-600">Delete this address?</span>
                    <button
                      type="button"
                      className={`rounded-md bg-red-600 px-3 py-1 font-medium text-white transition hover:bg-red-700 disabled:opacity-50 ${PRESS}`}
                      disabled={pending}
                      onClick={() => run(() => deleteAddress(address.id))}
                    >
                      {pending ? "Deleting..." : "Delete"}
                    </button>
                    <button type="button" className={LINK_BUTTON} onClick={() => setConfirmingId(null)} disabled={pending}>
                      Cancel
                    </button>
                  </div>
                ) : (
                  <div className="flex flex-wrap items-center gap-1">
                    {!address.isDefault && (
                      <button
                        type="button"
                        className={LINK_BUTTON}
                        disabled={pending}
                        onClick={() => run(() => makeDefaultAddress(address.id))}
                      >
                        Make default
                      </button>
                    )}
                    <button
                      type="button"
                      className={LINK_BUTTON}
                      disabled={pending}
                      onClick={() => {
                        setMode({ kind: "edit", id: address.id });
                        setNotice(null);
                      }}
                    >
                      Edit
                    </button>
                    <button
                      type="button"
                      className={`${LINK_BUTTON} text-red-700 hover:bg-red-50`}
                      disabled={pending}
                      onClick={() => setConfirmingId(address.id)}
                    >
                      Delete
                    </button>
                  </div>
                )}
              </li>
            ),
          )}
        </ul>
      )}
    </section>
  );
}

const INITIAL: AddressFormState = { status: "idle" };

const FIELDS: { name: AddressField; label: string; required: boolean; autoComplete?: string; wide?: boolean }[] = [
  { name: "label", label: "Name for this address", required: true },
  { name: "line1", label: "Street address", required: true, autoComplete: "address-line1", wide: true },
  { name: "line2", label: "Apartment, landmark (optional)", required: false, autoComplete: "address-line2", wide: true },
  { name: "city", label: "City", required: true, autoComplete: "address-level2" },
];

function AddressForm({
  address,
  districts,
  isFirst = false,
  onSaved,
  onCancel,
}: {
  address?: SavedAddress;
  districts: string[];
  isFirst?: boolean;
  onSaved: (message: string) => void;
  onCancel: () => void;
}) {
  const [state, formAction, pending] = useActionState(saveAddress, INITIAL);
  // Controlled fields: React resets uncontrolled ones after every action, which would wipe the
  // user's typing whenever validation fails.
  const [values, setValues] = useState<Record<AddressField, string>>({
    label: address?.label ?? "",
    line1: address?.line1 ?? "",
    line2: address?.line2 ?? "",
    city: address?.city ?? "",
    district: address?.district ?? "",
    postalCode: address?.postalCode ?? "",
  });

  // Close the form once per successful save, even if the parent re-renders first.
  const handledSave = useRef<number | undefined>(undefined);
  useEffect(() => {
    if (state.status === "saved" && state.savedAt && handledSave.current !== state.savedAt) {
      handledSave.current = state.savedAt;
      onSaved(state.message ?? "Address saved.");
    }
  }, [state.status, state.savedAt, state.message, onSaved]);

  const errors = state.fieldErrors ?? {};
  const prefix = address ? `edit-${address.id}` : "new";
  const field = (name: AddressField) => ({
    id: `${prefix}-${name}`,
    name,
    value: values[name],
    onChange: (event: { target: { value: string } }) => setValues((current) => ({ ...current, [name]: event.target.value })),
    "aria-invalid": errors[name] ? true : undefined,
    "aria-describedby": errors[name] ? `${prefix}-${name}-error` : undefined,
    className: "input aria-[invalid=true]:border-red-400 aria-[invalid=true]:focus:ring-red-100",
  });
  const errorText = (name: AddressField) =>
    errors[name] && (
      <p id={`${prefix}-${name}-error`} key={`${name}-${errors[name]}`} className={`text-sm text-red-700 ${FEEDBACK_MOTION}`}>
        {errors[name]}
      </p>
    );
  // A dropdown only when pricing supplied the district list; otherwise a text box. The choice must not
  // depend on what is typed, or the field would switch type mid-word. A saved district that pricing no
  // longer lists stays selectable.
  const useDropdown = districts.length > 0;
  const districtOptions =
    useDropdown && values.district && !districts.includes(values.district) ? [values.district, ...districts] : districts;

  return (
    <form action={formAction} noValidate className="space-y-4 rounded-xl border border-slate-200 bg-slate-50 p-4">
      <h3 className="text-sm font-semibold">{address ? `Edit ${address.label}` : "New address"}</h3>
      {address && <input type="hidden" name="id" value={address.id} />}

      <div className="grid gap-4 sm:grid-cols-2">
        {FIELDS.map(({ name, label, required, autoComplete, wide }) => (
          <div key={name} className={`space-y-1.5 ${wide ? "sm:col-span-2" : ""}`}>
            <label htmlFor={`${prefix}-${name}`} className="text-sm font-medium">{label}</label>
            <input {...field(name)} required={required} autoComplete={autoComplete} placeholder={name === "label" ? "Home, Office" : undefined} />
            {errorText(name)}
          </div>
        ))}

        <div className="space-y-1.5">
          <label htmlFor={`${prefix}-district`} className="text-sm font-medium">District</label>
          {useDropdown ? (
            <select {...field("district")} required>
              <option value="">Choose a district</option>
              {districtOptions.map((district) => (
                <option key={district} value={district}>{district}</option>
              ))}
            </select>
          ) : (
            <input {...field("district")} required placeholder="e.g. Kandy" />
          )}
          {errorText("district")}
        </div>

        <div className="space-y-1.5">
          <label htmlFor={`${prefix}-postalCode`} className="text-sm font-medium">
            Postal code <span className="font-normal text-slate-500">(optional)</span>
          </label>
          <input {...field("postalCode")} inputMode="numeric" maxLength={5} autoComplete="postal-code" placeholder="20000" />
          {errorText("postalCode")}
        </div>
      </div>

      {!address?.isDefault && !isFirst && (
        <label className="flex items-center gap-2 text-sm">
          <input type="checkbox" name="isDefault" className="h-4 w-4 rounded border-slate-300" />
          Use as my default address
        </label>
      )}
      {isFirst && <p className="text-xs text-slate-500">Your first address becomes your default.</p>}

      <div className="flex flex-wrap items-center gap-3">
        <button type="submit" className={`btn-primary ${PRESS}`} disabled={pending}>
          {pending ? "Saving..." : address ? "Save changes" : "Save address"}
        </button>
        <button type="button" className={`btn-secondary ${PRESS}`} onClick={onCancel} disabled={pending}>
          Cancel
        </button>
        <p role="status" aria-live="polite" className="text-sm">
          {!pending && state.status === "error" && state.message && (
            <span key={state.message + Object.keys(errors).join()} className={`inline-block text-red-700 ${FEEDBACK_MOTION}`}>
              {state.message}
            </span>
          )}
        </p>
      </div>
    </form>
  );
}
