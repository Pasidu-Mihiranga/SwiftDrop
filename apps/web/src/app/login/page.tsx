import type { Metadata } from "next";
import { redirect } from "next/navigation";
import { auth, signIn } from "@/auth";

export const metadata: Metadata = { title: "Sign in" };

function safeCallback(url: string | undefined): string {
  // Only same-site relative paths: prevents open redirects.
  return url && url.startsWith("/") && !url.startsWith("//") ? url : "/dashboard";
}

export default async function LoginPage({ searchParams }: { searchParams: Promise<{ callbackUrl?: string; error?: string }> }) {
  const { callbackUrl, error } = await searchParams;
  const redirectTo = safeCallback(callbackUrl);
  const session = await auth();
  if (session && !session.error) redirect(redirectTo);

  return (
    <div className="mx-auto max-w-md">
      <div className="card space-y-6">
        <div>
          <h1 className="text-2xl font-bold">Welcome to SwiftDrop</h1>
          <p className="mt-1 text-sm text-slate-600">Sign in or create an account to send and track parcels.</p>
        </div>
        {error && <p className="rounded-lg bg-red-50 p-3 text-sm text-red-700">Sign-in failed. Please try again.</p>}

        <form
          action={async () => {
            "use server";
            await signIn("keycloak", { redirectTo }, { kc_idp_hint: "google" });
          }}
        >
          <button type="submit" className="btn-secondary w-full">
            <svg aria-hidden width="18" height="18" viewBox="0 0 48 48">
              <path fill="#FFC107" d="M43.6 20.5H42V20H24v8h11.3C33.7 32.7 29.2 36 24 36c-6.6 0-12-5.4-12-12s5.4-12 12-12c3 0 5.8 1.1 7.9 3l5.7-5.7C34 6.1 29.3 4 24 4 12.9 4 4 12.9 4 24s8.9 20 20 20 20-8.9 20-20c0-1.3-.1-2.6-.4-3.5z" />
              <path fill="#FF3D00" d="m6.3 14.7 6.6 4.8C14.7 15.1 19 12 24 12c3 0 5.8 1.1 7.9 3l5.7-5.7C34 6.1 29.3 4 24 4 16.3 4 9.7 8.3 6.3 14.7z" />
              <path fill="#4CAF50" d="M24 44c5.2 0 9.9-2 13.4-5.2l-6.2-5.2C29.2 35.1 26.7 36 24 36c-5.2 0-9.6-3.3-11.3-8l-6.5 5C9.5 39.6 16.2 44 24 44z" />
              <path fill="#1976D2" d="M43.6 20.5H42V20H24v8h11.3c-.8 2.2-2.2 4.2-4.1 5.6l6.2 5.2C37 39.2 44 34 44 24c0-1.3-.1-2.6-.4-3.5z" />
            </svg>
            Continue with Google
          </button>
        </form>

        <div className="flex items-center gap-3 text-xs uppercase text-slate-400">
          <span className="h-px flex-1 bg-slate-200" />or<span className="h-px flex-1 bg-slate-200" />
        </div>

        <form
          action={async () => {
            "use server";
            await signIn("keycloak", { redirectTo });
          }}
        >
          <button type="submit" className="btn-primary w-full">Sign in with email</button>
        </form>
        <p className="text-center text-xs text-slate-500">New here? Choose &ldquo;Register&rdquo; on the next screen.</p>
      </div>
    </div>
  );
}
