import type { ReactNode } from "react";
import Link from "next/link";

/** Shared heading and width for the public privacy policy and terms pages. */
export function LegalDocument({
  title,
  children,
}: {
  title: string;
  children: ReactNode;
}) {
  return (
    <article className="mx-auto max-w-3xl">
      <h1 className="text-3xl font-bold text-slate-900">{title}</h1>
      <p className="mt-2 text-sm text-slate-500">Last updated 24 November 2026</p>
      <div className="mt-8 space-y-8 text-sm leading-relaxed text-slate-700">{children}</div>
      <p className="mt-10 text-sm text-slate-500">
        <Link href="/privacy" className="underline underline-offset-4 hover:text-slate-800">Privacy policy</Link>
        <span className="mx-2 text-slate-300">/</span>
        <Link href="/terms" className="underline underline-offset-4 hover:text-slate-800">Terms of service</Link>
      </p>
    </article>
  );
}

export function LegalSection({ title, children }: { title: string; children: ReactNode }) {
  return (
    <section className="space-y-3">
      <h2 className="text-lg font-semibold text-slate-900">{title}</h2>
      {children}
    </section>
  );
}
