"use client";

import { useRouter } from "next/navigation";
import { type ReactNode, createContext, useContext, useTransition } from "react";

interface Navigation {
  pending: boolean;
  /** Moves to another filter without a full reload; the current view stays until the new one is ready. */
  navigate: (href: string) => void;
  refresh: () => void;
}

const NavigationContext = createContext<Navigation | null>(null);

export function ReportsNavigation({ children }: { children: ReactNode }) {
  const router = useRouter();
  const [pending, startTransition] = useTransition();
  const value: Navigation = {
    pending,
    navigate: (href) => startTransition(() => router.push(href, { scroll: false })),
    refresh: () => startTransition(() => router.refresh()),
  };
  return <NavigationContext.Provider value={value}>{children}</NavigationContext.Provider>;
}

export function useReportsNavigation(): Navigation {
  const navigation = useContext(NavigationContext);
  if (!navigation) throw new Error("useReportsNavigation must be used inside <ReportsNavigation>");
  return navigation;
}

/**
 * Holds the previous render at reduced opacity while new data loads: no skeleton flash and no
 * layout jump on refetch.
 */
export function PendingFrame({ children }: { children: ReactNode }) {
  const { pending } = useReportsNavigation();
  return (
    <div aria-busy={pending} className={`transition-opacity duration-200 ${pending ? "pointer-events-none opacity-50" : ""}`}>
      {children}
    </div>
  );
}

export function RetryButton() {
  const { refresh, pending } = useReportsNavigation();
  return (
    <button type="button" onClick={refresh} disabled={pending} className="btn-secondary py-1.5 text-xs">
      {pending ? "Retrying..." : "Try again"}
    </button>
  );
}
