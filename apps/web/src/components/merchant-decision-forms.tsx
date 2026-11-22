"use client";

import { useActionState } from "react";
import { ActionMessage, SubmitButton } from "@/components/pricing-form";
import { MAX_REASON_LENGTH, type MerchantStatus } from "@/lib/merchant";
import { approveMerchant, rejectMerchant, suspendMerchant } from "@/lib/merchant-actions";
import type { ActionState } from "@/lib/pricing-actions";

const IDLE: ActionState = { status: "idle" };

/**
 * The decisions an admin can take on one application, offered only where the merchant lifecycle
 * allows them: approve or reject while PENDING, suspend once APPROVED. merchant-service enforces
 * the same rules and answers 422 if the application changed in the meantime.
 *
 * A successful decision refreshes the page, so the application leaves this status tab; a failure
 * stays on the row with the service's reason.
 */
export function MerchantDecisions({ merchantId, businessName, status }: {
  merchantId: string;
  businessName: string;
  status: MerchantStatus;
}) {
  if (status === "PENDING") {
    return (
      <div className="space-y-2">
        <ApproveForm merchantId={merchantId} businessName={businessName} />
        <RejectForm merchantId={merchantId} businessName={businessName} />
      </div>
    );
  }
  if (status === "APPROVED") {
    return <SuspendForm merchantId={merchantId} businessName={businessName} />;
  }
  return null;
}

function ApproveForm({ merchantId, businessName }: { merchantId: string; businessName: string }) {
  const [state, formAction] = useActionState(approveMerchant, IDLE);
  return (
    <div className="space-y-1">
      <form action={formAction} aria-label={`Approve ${businessName}`}>
        <input type="hidden" name="merchantId" value={merchantId} />
        <SubmitButton label="Approve" />
      </form>
      <ActionMessage state={state} />
    </div>
  );
}

function RejectForm({ merchantId, businessName }: { merchantId: string; businessName: string }) {
  const [state, formAction] = useActionState(rejectMerchant, IDLE);
  const id = `reason-${merchantId}`;
  return (
    <details className="text-sm">
      <summary className="cursor-pointer text-brand-700">Reject</summary>
      <form action={formAction} className="mt-2 space-y-2" aria-label={`Reject ${businessName}`}>
        <input type="hidden" name="merchantId" value={merchantId} />
        <label className="block text-xs font-medium text-slate-700" htmlFor={id}>
          Reason the applicant will read
        </label>
        <textarea
          id={id}
          name="reason"
          className="input"
          rows={3}
          maxLength={MAX_REASON_LENGTH}
          required
          aria-invalid={state.errors?.reason ? true : undefined}
        />
        <SubmitButton label="Reject application" />
        <ActionMessage state={state} />
      </form>
    </details>
  );
}

function SuspendForm({ merchantId, businessName }: { merchantId: string; businessName: string }) {
  const [state, formAction] = useActionState(suspendMerchant, IDLE);
  return (
    <details className="text-sm">
      <summary className="cursor-pointer text-brand-700">Suspend</summary>
      <form action={formAction} className="mt-2 space-y-2" aria-label={`Suspend ${businessName}`}>
        <input type="hidden" name="merchantId" value={merchantId} />
        <p className="text-xs text-slate-600">
          Its API orders are refused from then on. A suspension cannot be undone from this screen.
        </p>
        <label className="flex items-center gap-2 text-sm text-slate-700">
          <input type="checkbox" name="confirm" required />
          Yes, stop {businessName}&apos;s API access
        </label>
        <SubmitButton label="Suspend merchant" />
        <ActionMessage state={state} />
      </form>
    </details>
  );
}
