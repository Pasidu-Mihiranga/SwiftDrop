"use client";

import { useActionState } from "react";
import { ActionMessage, SubmitButton } from "@/components/pricing-form";
import { closeRateCard, type ActionState } from "@/lib/pricing-actions";

const IDLE: ActionState = { status: "idle" };

/**
 * Ends a rate. The component stays mounted once the card has closed, because closing it is what
 * removes the form, and the administrator still needs to see that it worked.
 */
export function CloseRateForm({
  rateCardId,
  effectiveFrom,
  closed,
}: {
  rateCardId: string;
  effectiveFrom: string;
  closed: boolean;
}) {
  const [state, formAction] = useActionState(closeRateCard, IDLE);

  if (closed) return <ActionMessage state={state} />;

  return (
    <details className="text-sm">
      <summary className="cursor-pointer text-brand-700">Close this rate</summary>
      <div className="mt-2 space-y-1">
        <form action={formAction} className="flex flex-col gap-2 sm:flex-row sm:items-end">
          <input type="hidden" name="rateCardId" value={rateCardId} />
          <span className="flex-1">
            <label className="block text-xs font-medium text-slate-700" htmlFor={`to-${rateCardId}`}>
              Stops applying on
            </label>
            <input
              id={`to-${rateCardId}`}
              name="effectiveTo"
              type="date"
              className="input"
              min={effectiveFrom}
              required
            />
          </span>
          <SubmitButton label="Close rate" />
        </form>
        <ActionMessage state={state} />
        <p className="text-xs text-slate-500">
          Closing a rate without adding a replacement leaves the lane unquotable from that date.
        </p>
      </div>
    </details>
  );
}
