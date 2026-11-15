import { type Stepper, STATUS_LABELS } from "@/lib/tracking";

/** Delivery progress. Vertical on phones; the furthest reached step is marked as the current one. */
export function TrackingStepper({ stepper }: { stepper: Stepper }) {
  return (
    <div>
      <ol className="mt-6 space-y-4">
        {stepper.steps.map((step) => (
          <li
            key={step.status}
            aria-current={step.state === "current" ? "step" : undefined}
            className="flex items-center gap-3"
            data-state={step.state}
          >
            <span
              aria-hidden="true"
              className={`h-3 w-3 shrink-0 rounded-full ${
                step.state === "todo" ? "bg-slate-300" : step.state === "current" ? "bg-brand-600" : "bg-brand-300"
              }`}
            />
            <span className={step.state === "todo" ? "text-slate-500" : "font-medium text-slate-900"}>{step.label}</span>
          </li>
        ))}
      </ol>
      {stepper.outcome ? (
        <p role="status" className="mt-4 rounded-lg bg-red-50 p-3 text-sm text-red-700">
          {STATUS_LABELS[stepper.outcome]}: this parcel did not reach its recipient. Contact SwiftDrop for the next step.
        </p>
      ) : null}
    </div>
  );
}
