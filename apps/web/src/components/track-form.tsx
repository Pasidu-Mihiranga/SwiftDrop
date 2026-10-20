import { redirect } from "next/navigation";

/** Public tracking-number search (works without JavaScript: plain form + server action). */
export function TrackForm({ defaultValue = "" }: { defaultValue?: string }) {
  async function track(formData: FormData) {
    "use server";
    const value = String(formData.get("trackingNumber") ?? "").trim().toUpperCase();
    if (/^SD[0-9A-Z]{8,20}$/.test(value)) redirect(`/track/${value}`);
    redirect("/track?invalid=1");
  }

  return (
    <form action={track} className="flex w-full flex-col gap-2 sm:flex-row">
      <label htmlFor="trackingNumber" className="sr-only">Tracking number</label>
      <input
        id="trackingNumber"
        name="trackingNumber"
        defaultValue={defaultValue}
        placeholder="Tracking number, e.g. SD261020K7Q2M9X"
        className="input sm:flex-1"
        autoComplete="off"
        required
      />
      <button className="btn-primary" type="submit">Track parcel</button>
    </form>
  );
}
