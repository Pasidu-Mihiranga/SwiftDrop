import Link from "next/link";

export default function ForbiddenPage() {
  return (
    <div className="card mx-auto max-w-md text-center">
      <h1 className="text-xl font-bold">You don&rsquo;t have access to that page</h1>
      <p className="mt-2 text-sm text-slate-600">Your account doesn&rsquo;t have the role this area needs.</p>
      <Link href="/dashboard" className="btn-primary mt-6">Go to my dashboard</Link>
    </div>
  );
}
