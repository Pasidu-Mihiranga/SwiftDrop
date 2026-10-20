import type { Metadata } from "next";
import { TrackForm } from "@/components/track-form";

export const metadata: Metadata = { title: "Track a parcel" };

export default async function TrackSearchPage({ searchParams }: { searchParams: Promise<{ invalid?: string }> }) {
  const { invalid } = await searchParams;
  return (
    <div className="mx-auto max-w-xl space-y-4">
      <h1 className="text-2xl font-bold">Track a parcel</h1>
      {invalid && <p className="text-sm text-red-700">That doesn&rsquo;t look like a SwiftDrop tracking number.</p>}
      <TrackForm />
    </div>
  );
}
