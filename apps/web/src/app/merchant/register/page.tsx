export const metadata = { title: "Become a merchant" };

/** Any signed-in user can apply; an admin approves the application (Phase 1, M7). */
export default function MerchantRegisterPage() {
  return (
    <div className="card mx-auto max-w-xl space-y-3">
      <h1 className="text-2xl font-bold">Become a SwiftDrop merchant</h1>
      <p className="text-sm text-slate-600">
        Register your business to create deliveries from your online store through our API. An administrator
        reviews every application before API access is granted.
      </p>
      <p className="rounded-lg bg-brand-50 p-3 text-sm text-brand-700">The registration form is delivered in Phase 1 (merchant-service, M7).</p>
    </div>
  );
}
