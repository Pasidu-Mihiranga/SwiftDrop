"use client";

import Link from "next/link";
import { useActionState, useEffect, useRef, useState, useTransition } from "react";
import {
  ROLE_LABELS,
  STAFF_ROLES,
  staffRolesOf,
  VEHICLE_LABELS,
  VEHICLE_TYPES,
  visibleRoles,
  type AdminUser,
  type AdminUserPage,
  type StaffField,
  type StaffFormState,
} from "@/lib/admin-users";
import { FEEDBACK_MOTION, PRESS } from "@/lib/motion";
import { createStaffAccount, resendInvitation, setAccountEnabled, setStaffRoles, type UserActionResult } from "./actions";

interface Notice {
  ok: boolean;
  message: string;
  key: number;
}

interface Props {
  page: AdminUserPage;
  currentUserId: string;
  zones: { id: string; label: string }[];
  previousHref: string | null;
  nextHref: string | null;
}

const LINK_BUTTON = `rounded-md px-2 py-1 text-sm font-medium text-brand-700 transition hover:bg-brand-50 disabled:opacity-50 ${PRESS}`;

/**
 * The account list and the "Add staff account" form. Accounts are rendered by the server (the page
 * re-renders after every change); this component only holds which row is being edited and the last
 * result message.
 */
export function UserAdmin({ page, currentUserId, zones, previousHref, nextHref }: Props) {
  const [creating, setCreating] = useState(false);
  const [notice, setNotice] = useState<Notice | null>(null);
  const [rolesFor, setRolesFor] = useState<string | null>(null);
  const [confirmDisable, setConfirmDisable] = useState<string | null>(null);
  const [pending, startTransition] = useTransition();

  function run(action: () => Promise<UserActionResult>) {
    startTransition(async () => {
      const result = await action();
      setNotice({ ...result, key: Date.now() });
      if (result.ok) {
        setRolesFor(null);
        setConfirmDisable(null);
      }
    });
  }

  return (
    <section className="card space-y-4" aria-labelledby="accounts-heading">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h2 id="accounts-heading" className="font-semibold">Accounts</h2>
          <p className="text-sm text-slate-500">
            {page.total === 0 ? "No accounts match." : `${page.total} account${page.total === 1 ? "" : "s"}`}
          </p>
        </div>
        {!creating && (
          <button type="button" className={`btn-primary ${PRESS}`} onClick={() => { setCreating(true); setNotice(null); }}>
            Add staff account
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

      {creating && (
        <StaffForm
          zones={zones}
          onCreated={(message) => {
            setCreating(false);
            setNotice({ ok: true, message, key: Date.now() });
          }}
          onCancel={() => setCreating(false)}
        />
      )}

      <ul className="divide-y divide-slate-100">
        {page.items.map((user) => {
          const isSelf = user.id === currentUserId;
          return (
            <li key={user.id} className="space-y-3 py-4">
              <div className="flex flex-wrap items-start justify-between gap-3">
                <div className="min-w-0 space-y-1">
                  <p className="flex flex-wrap items-center gap-2 font-medium">
                    {user.fullName}
                    {isSelf && <span className="text-xs font-normal text-slate-500">(you)</span>}
                    {visibleRoles(user).map((role) => (
                      <span key={role} className="rounded-full bg-brand-50 px-2 py-0.5 text-xs font-medium text-brand-700">
                        {ROLE_LABELS[role] ?? role}
                      </span>
                    ))}
                    <span
                      className={`rounded-full px-2 py-0.5 text-xs font-medium ${
                        user.enabled ? "bg-emerald-50 text-emerald-700" : "bg-slate-100 text-slate-600"
                      }`}
                    >
                      {user.enabled ? "Active" : "Disabled"}
                    </span>
                  </p>
                  <p className="break-all text-sm text-slate-600">{user.email ?? "No e-mail"}</p>
                </div>
                {confirmDisable === user.id ? (
                  <div className="flex flex-wrap items-center gap-2 text-sm" role="group" aria-label={`Disable ${user.fullName}?`}>
                    <span className="text-slate-600">Disable this account? They are signed out at once.</span>
                    <button
                      type="button"
                      className={`rounded-md bg-red-600 px-3 py-1 font-medium text-white transition hover:bg-red-700 disabled:opacity-50 ${PRESS}`}
                      disabled={pending}
                      onClick={() => run(() => setAccountEnabled(user.id, false, user.fullName))}
                    >
                      {pending ? "Disabling..." : "Disable"}
                    </button>
                    <button type="button" className={LINK_BUTTON} disabled={pending} onClick={() => setConfirmDisable(null)}>
                      Cancel
                    </button>
                  </div>
                ) : (
                  <div className="flex flex-wrap items-center gap-1">
                    {staffRolesOf(user).includes("COURIER") && (
                      <Link href="/admin/couriers" className={LINK_BUTTON}>Courier details</Link>
                    )}
                    <button
                      type="button"
                      className={LINK_BUTTON}
                      disabled={pending}
                      onClick={() => { setRolesFor(rolesFor === user.id ? null : user.id); setNotice(null); }}
                    >
                      Edit roles
                    </button>
                    {user.email && (
                      <button type="button" className={LINK_BUTTON} disabled={pending}
                        onClick={() => run(() => resendInvitation(user.id, user.email as string))}>
                        Send invite again
                      </button>
                    )}
                    {user.enabled ? (
                      !isSelf && (
                        <button type="button" className={`${LINK_BUTTON} text-red-700 hover:bg-red-50`} disabled={pending}
                          onClick={() => setConfirmDisable(user.id)}>
                          Disable
                        </button>
                      )
                    ) : (
                      <button type="button" className={LINK_BUTTON} disabled={pending}
                        onClick={() => run(() => setAccountEnabled(user.id, true, user.fullName))}>
                        Enable
                      </button>
                    )}
                  </div>
                )}
              </div>
              {rolesFor === user.id && (
                <RoleEditor
                  user={user}
                  isSelf={isSelf}
                  pending={pending}
                  onSave={(roles) => run(() => setStaffRoles(user.id, roles, user.fullName))}
                  onCancel={() => setRolesFor(null)}
                />
              )}
            </li>
          );
        })}
      </ul>

      {(previousHref || nextHref) && (
        <nav aria-label="Pages" className="flex items-center justify-between text-sm">
          {previousHref ? <Link href={previousHref} className={LINK_BUTTON}>Previous</Link> : <span />}
          <span className="text-slate-500">Page {page.page + 1} of {Math.max(1, Math.ceil(page.total / page.size))}</span>
          {nextHref ? <Link href={nextHref} className={LINK_BUTTON}>Next</Link> : <span />}
        </nav>
      )}
    </section>
  );
}

function RoleEditor({ user, isSelf, pending, onSave, onCancel }: {
  user: AdminUser;
  isSelf: boolean;
  pending: boolean;
  onSave: (roles: string[]) => void;
  onCancel: () => void;
}) {
  const [selected, setSelected] = useState<string[]>(staffRolesOf(user));
  const unchanged = [...selected].sort().join() === [...staffRolesOf(user)].sort().join();

  return (
    <fieldset className="space-y-3 rounded-xl border border-slate-200 bg-slate-50 p-4">
      <legend className="px-1 text-sm font-semibold">Staff roles for {user.fullName}</legend>
      <div className="flex flex-wrap gap-4">
        {STAFF_ROLES.map((role) => {
          const locked = isSelf && role === "ADMIN";
          return (
            <label key={role} className="flex items-center gap-2 text-sm">
              <input
                type="checkbox"
                className="h-4 w-4 rounded border-slate-300"
                checked={selected.includes(role)}
                disabled={locked || pending}
                onChange={(event) =>
                  setSelected((current) => (event.target.checked ? [...current, role] : current.filter((r) => r !== role)))
                }
              />
              {ROLE_LABELS[role]}
            </label>
          );
        })}
      </div>
      <p className="text-xs text-slate-500">
        {isSelf ? "You cannot remove your own admin role. " : ""}Customer and merchant access are not changed here.
      </p>
      <div className="flex gap-3">
        <button type="button" className={`btn-primary ${PRESS}`} disabled={pending || unchanged} onClick={() => onSave(selected)}>
          {pending ? "Saving..." : "Save roles"}
        </button>
        <button type="button" className={`btn-secondary ${PRESS}`} disabled={pending} onClick={onCancel}>Cancel</button>
      </div>
    </fieldset>
  );
}

const INITIAL: StaffFormState = { status: "idle" };
type Values = Record<StaffField, string>;
const EMPTY: Values = { email: "", fullName: "", phone: "", role: "COURIER", vehicleType: "", homeZoneId: "", maxDailyCapacity: "" };

function StaffForm({ zones, onCreated, onCancel }: {
  zones: { id: string; label: string }[];
  onCreated: (message: string) => void;
  onCancel: () => void;
}) {
  const [state, formAction, pending] = useActionState(createStaffAccount, INITIAL);
  // Controlled fields: React resets uncontrolled ones after every action, which would wipe the
  // admin's typing whenever validation fails.
  const [values, setValues] = useState<Values>(EMPTY);
  const handled = useRef<number | undefined>(undefined);
  useEffect(() => {
    if (state.status === "created" && state.createdAt && handled.current !== state.createdAt) {
      handled.current = state.createdAt;
      onCreated(state.message ?? "Account created.");
    }
  }, [state.status, state.createdAt, state.message, onCreated]);

  const errors = state.fieldErrors ?? {};
  const field = (name: StaffField) => ({
    id: `staff-${name}`,
    name,
    value: values[name],
    onChange: (event: { target: { value: string } }) => setValues((current) => ({ ...current, [name]: event.target.value })),
    "aria-invalid": errors[name] ? true : undefined,
    "aria-describedby": errors[name] ? `staff-${name}-error` : undefined,
    className: "input aria-[invalid=true]:border-red-400 aria-[invalid=true]:focus:ring-red-100",
  });
  const errorText = (name: StaffField) =>
    errors[name] && (
      <p id={`staff-${name}-error`} key={`${name}-${errors[name]}`} className={`text-sm text-red-700 ${FEEDBACK_MOTION}`}>
        {errors[name]}
      </p>
    );
  const label = (name: StaffField, text: string, optional = false) => (
    <label htmlFor={`staff-${name}`} className="text-sm font-medium">
      {text} {optional && <span className="font-normal text-slate-500">(optional)</span>}
    </label>
  );

  return (
    <form action={formAction} noValidate className="space-y-4 rounded-xl border border-slate-200 bg-slate-50 p-4">
      <h3 className="text-sm font-semibold">New staff account</h3>
      <p className="text-xs text-slate-500">
        The person receives an e-mail to set their own password. SwiftDrop never sees it.
      </p>
      <div className="grid gap-4 sm:grid-cols-2">
        <div className="space-y-1.5">
          {label("email", "E-mail")}
          <input {...field("email")} type="email" autoComplete="off" />
          {errorText("email")}
        </div>
        <div className="space-y-1.5">
          {label("fullName", "Full name")}
          <input {...field("fullName")} maxLength={100} autoComplete="off" />
          {errorText("fullName")}
        </div>
        <div className="space-y-1.5">
          {label("phone", "Mobile number", true)}
          <input {...field("phone")} type="tel" inputMode="tel" placeholder="0771234567" />
          {errorText("phone")}
        </div>
        <div className="space-y-1.5">
          {label("role", "Role")}
          <select {...field("role")}>
            {STAFF_ROLES.map((role) => (
              <option key={role} value={role}>{ROLE_LABELS[role]}</option>
            ))}
          </select>
          {errorText("role")}
        </div>
      </div>

      {values.role === "COURIER" && (
        <fieldset className="space-y-3">
          <legend className="text-sm font-medium">
            Dispatch details <span className="font-normal text-slate-500">(optional, all three or none)</span>
          </legend>
          <div className="grid gap-4 sm:grid-cols-3">
            <div className="space-y-1.5">
              {label("vehicleType", "Vehicle")}
              <select {...field("vehicleType")}>
                <option value="">Choose</option>
                {VEHICLE_TYPES.map((vehicle) => (
                  <option key={vehicle} value={vehicle}>{VEHICLE_LABELS[vehicle]}</option>
                ))}
              </select>
              {errorText("vehicleType")}
            </div>
            <div className="space-y-1.5">
              {label("homeZoneId", "Home zone")}
              {zones.length > 0 ? (
                <select {...field("homeZoneId")}>
                  <option value="">Choose</option>
                  {zones.map((zone) => (
                    <option key={zone.id} value={zone.id}>{zone.label}</option>
                  ))}
                </select>
              ) : (
                <p className="text-sm text-slate-500">Zones are not available right now; set them later on the Couriers page.</p>
              )}
              {errorText("homeZoneId")}
            </div>
            <div className="space-y-1.5">
              {label("maxDailyCapacity", "Parcels per day")}
              <input {...field("maxDailyCapacity")} inputMode="numeric" placeholder="25" />
              {errorText("maxDailyCapacity")}
            </div>
          </div>
        </fieldset>
      )}

      <div className="flex flex-wrap items-center gap-3">
        <button type="submit" className={`btn-primary ${PRESS}`} disabled={pending}>
          {pending ? "Creating..." : "Create account"}
        </button>
        <button type="button" className={`btn-secondary ${PRESS}`} disabled={pending} onClick={onCancel}>Cancel</button>
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
