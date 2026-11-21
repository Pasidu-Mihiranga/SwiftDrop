import { describe, expect, it, vi } from "vitest";
import { fireEvent, render, screen } from "@testing-library/react";

const { pathname } = vi.hoisted(() => ({ pathname: { current: "/courier" } }));
vi.mock("next/navigation", () => ({ usePathname: () => pathname.current }));

import { MobileNav } from "./mobile-nav";

const ITEMS = [
  { href: "/track", label: "Track" },
  { href: "/courier", label: "Today's runs" },
];

describe("MobileNav", () => {
  it("keeps the links behind one Menu button until it is opened", () => {
    render(<MobileNav items={ITEMS}><button type="button">Sign out</button></MobileNav>);

    const toggle = screen.getByRole("button", { name: "Menu" });
    expect(toggle).toHaveAttribute("aria-expanded", "false");
    expect(screen.queryByRole("link", { name: "Track" })).not.toBeInTheDocument();

    fireEvent.click(toggle);

    expect(screen.getByRole("button", { name: "Close" })).toHaveAttribute("aria-expanded", "true");
    expect(screen.getByRole("link", { name: "Today's runs" })).toHaveAttribute("aria-current", "page");
    expect(screen.getByRole("button", { name: "Sign out" })).toBeInTheDocument();
  });

  it("closes on Escape and when the route changes", () => {
    const { rerender } = render(<MobileNav items={ITEMS} />);

    fireEvent.click(screen.getByRole("button", { name: "Menu" }));
    fireEvent.keyDown(window, { key: "Escape" });
    expect(screen.queryByRole("link", { name: "Track" })).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Menu" }));
    expect(screen.getByRole("link", { name: "Track" })).toBeInTheDocument();
    pathname.current = "/track";
    rerender(<MobileNav items={ITEMS} />);
    expect(screen.queryByRole("link", { name: "Track" })).not.toBeInTheDocument();
  });
});
