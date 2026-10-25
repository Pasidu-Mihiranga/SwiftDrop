"use client";

import { useActionState } from "react";
import { useFormStatus } from "react-dom";
import type { ActionState } from "@/lib/pricing-actions";

const IDLE: ActionState = { status: "idle" };

/**
 * Wraps an admin pricing server action so the form can report pending state and whatever the
 * service answered, including field-level validation messages.
 */
export function PricingForm({
  action,
  submitLabel,
  children,
  className = "space-y-3",
}: {
  action: (previous: ActionState, formData: FormData) => Promise<ActionState>;
  submitLabel: string;
  children?: React.ReactNode | ((state: ActionState) => React.ReactNode);
  className?: string;
}) {
  const [state, formAction] = useActionState(action, IDLE);

  return (
    <form action={formAction} className={className}>
      {typeof children === "function" ? children(state) : children}
      <SubmitButton label={submitLabel} />
      <ActionMessage state={state} />
    </form>
  );
}

function SubmitButton({ label }: { label: string }) {
  const { pending } = useFormStatus();
  return (
    <button className="btn-primary w-full sm:w-auto" type="submit" disabled={pending}>
      {pending ? "Saving..." : label}
    </button>
  );
}

function ActionMessage({ state }: { state: ActionState }) {
  if (state.status === "idle") return null;

  const fieldMessages = Object.entries(state.errors ?? {});
  const tone =
    state.status === "ok" ? "bg-emerald-50 text-emerald-800" : "bg-red-50 text-red-700";

  return (
    <div role="status" aria-live="polite" className={`rounded-lg p-3 text-sm ${tone}`}>
      <p>{state.message ?? (state.status === "ok" ? "Saved." : "That did not work.")}</p>
      {fieldMessages.length > 0 ? (
        <ul className="mt-1 list-inside list-disc">
          {fieldMessages.map(([field, message]) => (
            <li key={field}>
              {field}: {message}
            </li>
          ))}
        </ul>
      ) : null}
    </div>
  );
}
