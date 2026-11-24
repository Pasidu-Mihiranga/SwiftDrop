"use client";

import { usePathname } from "next/navigation";

/**
 * The page container. The landing page draws its own full-width bands, so it gets a bare <main>;
 * every other screen keeps the centred working width it was designed for.
 */
export function MainFrame({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();
  if (pathname === "/") {
    return <main id="main">{children}</main>;
  }
  return (
    <main id="main" className="mx-auto w-full max-w-6xl px-4 py-8 pb-24 sm:px-6">
      {children}
    </main>
  );
}
