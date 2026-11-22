"use client";

import { useActionState, useState } from "react";
import { useFormStatus } from "react-dom";
import type { ApiCredentials, CredentialsState } from "@/lib/merchant";
import { rotateCredentials } from "@/lib/merchant-actions";

const IDLE: CredentialsState = { status: "idle" };

/**
 * Generates the merchant's API client secret and shows it once. The secret lives only in this
 * component's state: it is not written to storage or the address bar, and leaving or reloading
 * the page loses it, after which the only way back is to generate a new one.
 */
export function MerchantCredentialsForm() {
  const [state, formAction] = useActionState(rotateCredentials, IDLE);
  const [hiddenFor, setHiddenFor] = useState<string | null>(null);
  const credentials = state.status === "ok" ? state.credentials : undefined;
  const showSecret = credentials && hiddenFor !== credentials.clientSecret;

  return (
    <div className="space-y-4">
      {showSecret ? (
        <NewSecret credentials={credentials} onHide={() => setHiddenFor(credentials.clientSecret)} />
      ) : null}
      {credentials && !showSecret ? (
        <p className="notice-info" role="status">
          The secret is hidden and cannot be shown again. If you did not save it, generate a new one.
        </p>
      ) : null}

      <form action={formAction} className="space-y-3" aria-label="Generate a new client secret">
        <p className="text-sm text-slate-600">
          Generating a new secret replaces the current one straight away: your system will need the new secret
          the next time it asks for a token. Tokens it already holds keep working until they expire.
        </p>
        <label className="flex items-start gap-2 text-sm text-slate-700">
          <input type="checkbox" name="confirm" required className="mt-0.5" />
          I understand that the current secret stops working.
        </label>
        <GenerateButton />
        {state.status === "error" ? (
          <p className="notice-error" role="alert">{state.message}</p>
        ) : null}
      </form>
    </div>
  );
}

function GenerateButton() {
  const { pending } = useFormStatus();
  return (
    <button className="btn-primary w-full sm:w-auto" type="submit" disabled={pending}>
      {pending ? "Generating..." : "Generate new secret"}
    </button>
  );
}

function NewSecret({ credentials, onHide }: { credentials: ApiCredentials; onHide: () => void }) {
  return (
    <section className="notice-warning space-y-3" aria-label="Your new credentials">
      <p className="font-medium" role="status">
        Copy the client secret now. For your security it is shown only once and SwiftDrop does not keep a copy.
      </p>
      <CopyField label="Client ID" value={credentials.clientId} />
      <CopyField label="Client secret" value={credentials.clientSecret} />
      <CopyField label="Token URL" value={credentials.tokenUrl} />
      <details className="text-sm">
        <summary className="cursor-pointer text-brand-700">How your system gets a token</summary>
        <pre className="mt-2 overflow-x-auto rounded-lg bg-white p-3 text-xs text-slate-800">
{`curl -X POST ${credentials.tokenUrl} \\
  -d grant_type=client_credentials \\
  -d client_id=${credentials.clientId} \\
  --data-urlencode client_secret=<your secret>`}
        </pre>
        <p className="mt-1 text-xs text-slate-600">
          Send the access token as <code>Authorization: Bearer …</code> to <code>POST /api/v1/merchant/orders</code>,
          with an <code>Idempotency-Key</code> header for each new order.
        </p>
      </details>
      <button type="button" className="btn-secondary" onClick={onHide}>
        I have saved it — hide the secret
      </button>
    </section>
  );
}

function CopyField({ label, value }: { label: string; value: string }) {
  const [copied, setCopied] = useState(false);
  const id = `credential-${label.toLowerCase().replace(/\s+/g, "-")}`;

  async function copy() {
    try {
      await navigator.clipboard.writeText(value);
      setCopied(true);
    } catch {
      setCopied(false);
    }
  }

  return (
    <div className="space-y-1">
      <label className="block text-xs font-medium text-slate-700" htmlFor={id}>{label}</label>
      <div className="flex gap-2">
        <input id={id} className="input font-mono" value={value} readOnly onFocus={(event) => event.target.select()} />
        <button type="button" className="btn-secondary" onClick={copy} aria-label={`Copy ${label}`}>
          {copied ? "Copied" : "Copy"}
        </button>
      </div>
    </div>
  );
}
