import Link from "next/link";
import { TrackForm } from "@/components/track-form";

const features = [
  { title: "Know the price first", text: "Get a fee estimate for any two districts before you sign up." },
  { title: "Live tracking", text: "Follow every step from pickup to doorstep in real time." },
  { title: "For online stores", text: "Create orders from your shop via our API and get webhooks." },
];

export default function HomePage() {
  return (
    <div className="space-y-12">
      <section className="rounded-3xl bg-gradient-to-br from-brand-600 to-brand-700 px-6 py-12 text-white sm:px-12">
        <h1 className="max-w-2xl text-3xl font-bold leading-tight sm:text-5xl">Island-wide parcel delivery you can actually see.</h1>
        <p className="mt-4 max-w-xl text-brand-100">From Colombo to Jaffna — book, pay and track every parcel live.</p>
        <div className="mt-8 max-w-2xl rounded-2xl bg-white p-3">
          <TrackForm />
        </div>
        <div className="mt-6 flex flex-wrap gap-3">
          <Link href="/login" className="btn bg-white text-brand-700 hover:bg-brand-50">Send a parcel</Link>
          <Link href="/quote" className="btn border border-white/40 text-white hover:bg-white/10">Check a price</Link>
          <Link href="/merchant/register" className="btn border border-white/40 text-white hover:bg-white/10">Become a merchant</Link>
        </div>
      </section>

      <section className="grid gap-4 sm:grid-cols-3">
        {features.map((f) => (
          <div key={f.title} className="card">
            <h2 className="font-semibold">{f.title}</h2>
            <p className="mt-2 text-sm text-slate-600">{f.text}</p>
          </div>
        ))}
      </section>
    </div>
  );
}
