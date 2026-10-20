import type { Metadata } from "next";
import { TrackForm } from "@/components/track-form";

const STEPS = ["Created", "Picked up", "In transit", "Out for delivery", "Delivered"];

export async function generateMetadata({ params }: { params: Promise<{ trackingNumber: string }> }): Promise<Metadata> {
  const { trackingNumber } = await params;
  return { title: `Tracking ${trackingNumber}` };
}

/** Public, mobile-first tracking page. Live data arrives in Phase 2 (tracking-service, M6). */
export default async function TrackPage({ params }: { params: Promise<{ trackingNumber: string }> }) {
  const { trackingNumber } = await params;
  return (
    <div className="mx-auto max-w-xl space-y-6">
      <TrackForm defaultValue={trackingNumber} />
      <div className="card">
        <p className="text-xs uppercase tracking-wide text-slate-500">Tracking number</p>
        <h1 className="font-mono text-xl font-bold">{trackingNumber}</h1>
        <ol className="mt-6 space-y-4">
          {STEPS.map((step) => (
            <li key={step} className="flex items-center gap-3">
              <span className="h-3 w-3 rounded-full bg-slate-300" />
              <span className="text-slate-500">{step}</span>
            </li>
          ))}
        </ol>
        <p className="mt-6 rounded-lg bg-brand-50 p-3 text-sm text-brand-700">
          Live status updates are connected in Phase 2 (tracking-service + WebSocket).
        </p>
      </div>
    </div>
  );
}
