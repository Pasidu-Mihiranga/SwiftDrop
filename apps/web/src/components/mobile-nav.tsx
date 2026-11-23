"use client";

import { useEffect, useState } from "react";
import { usePathname } from "next/navigation";
import { NavLinks, type NavItem } from "@/components/nav-links";

/**
 * The header's links on a phone: one "Menu" button instead of a block of wrapped links pushing the
 * page down (the courier screens are used on phones). It closes again when a link is followed.
 * `children` carries the account actions, which are server-rendered (sign-out is a server action).
 */
export function MobileNav({ items, children }: { items: readonly NavItem[]; children?: React.ReactNode }) {
  const [open, setOpen] = useState(false);
  const pathname = usePathname();
  const [shownFor, setShownFor] = useState(pathname);

  // Close the panel once the route changes, without an effect that sets state on every render.
  if (pathname !== shownFor) {
    setShownFor(pathname);
    setOpen(false);
  }

  useEffect(() => {
    if (!open) return;
    const onKey = (event: KeyboardEvent) => {
      if (event.key === "Escape") setOpen(false);
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [open]);

  return (
    <div className="md:hidden">
      <button
        type="button"
        className="btn-secondary px-3 py-2 text-sm"
        aria-expanded={open}
        aria-controls="mobile-nav-panel"
        onClick={() => setOpen((value) => !value)}
      >
        {open ? "Close" : "Menu"}
      </button>
      {open ? (
        <div
          id="mobile-nav-panel"
          className="absolute inset-x-0 top-full z-40 border-b border-slate-200 bg-white px-4 pb-4 pt-2 shadow-lg"
        >
          <nav aria-label="Primary (mobile)" className="flex flex-col gap-1 text-sm">
            <NavLinks items={items} />
          </nav>
          {children ? <div className="mt-3 border-t border-slate-100 pt-3">{children}</div> : null}
        </div>
      ) : null}
    </div>
  );
}
