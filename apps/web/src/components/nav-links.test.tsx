import { describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import { isNavItemActive, NavLink, NavLinks } from "./nav-links";

const mockUsePathname = vi.fn<() => string>();

vi.mock("next/navigation", () => ({
  usePathname: () => mockUsePathname(),
}));

describe("isNavItemActive", () => {
  const customerHrefs = ["/track", "/quote", "/customer", "/customer/history"];
  const adminHrefs = ["/track", "/quote", "/admin", "/admin/notifications/sms-inbox"];

  it("matches /quote cleanly", () => {
    expect(isNavItemActive("/quote", "/quote", customerHrefs)).toBe(true);
    expect(isNavItemActive("/quote", "/track", customerHrefs)).toBe(false);
    expect(isNavItemActive("/quote", "/customer", customerHrefs)).toBe(false);
  });

  it("matches nested tracking routes under /track", () => {
    expect(isNavItemActive("/track", "/track", customerHrefs)).toBe(true);
    expect(isNavItemActive("/track/SD-123456", "/track", customerHrefs)).toBe(true);
    expect(isNavItemActive("/track/SD-123456", "/quote", customerHrefs)).toBe(false);
  });

  it("keeps My deliveries highlighted on /customer and /customer/book", () => {
    expect(isNavItemActive("/customer", "/customer", customerHrefs)).toBe(true);
    expect(isNavItemActive("/customer/book", "/customer", customerHrefs)).toBe(true);
    expect(isNavItemActive("/customer/parcels/abc", "/customer", customerHrefs)).toBe(true);
    expect(isNavItemActive("/customer/book", "/customer/history", customerHrefs)).toBe(false);
  });

  it("prefers the more specific /customer/history over /customer", () => {
    expect(isNavItemActive("/customer/history", "/customer/history", customerHrefs)).toBe(true);
    expect(isNavItemActive("/customer/history", "/customer", customerHrefs)).toBe(false);
  });

  it("handles /admin routes and /admin/notifications/sms-inbox longest-prefix match", () => {
    expect(isNavItemActive("/admin", "/admin", adminHrefs)).toBe(true);
    expect(isNavItemActive("/admin/pricing/zones", "/admin", adminHrefs)).toBe(true);
    expect(isNavItemActive("/admin/notifications/sms-inbox", "/admin/notifications/sms-inbox", adminHrefs)).toBe(true);
    expect(isNavItemActive("/admin/notifications/sms-inbox", "/admin", adminHrefs)).toBe(false);
  });

  it("matches /account for My profile", () => {
    expect(isNavItemActive("/account", "/account", ["/account"])).toBe(true);
    expect(isNavItemActive("/quote", "/account", ["/account"])).toBe(false);
  });
});

describe("NavLinks and NavLink components", () => {
  it("sets aria-current='page' and brand highlight class only on the active link", () => {
    mockUsePathname.mockReturnValue("/quote");
    render(
      <nav>
        <NavLinks
          items={[
            { href: "/track", label: "Track" },
            { href: "/quote", label: "Get a price" },
            { href: "/customer", label: "My deliveries" },
          ]}
        />
        <NavLink href="/account" label="My profile" className="text-sm" />
      </nav>,
    );

    const quoteLink = screen.getByRole("link", { name: "Get a price" });
    const trackLink = screen.getByRole("link", { name: "Track" });
    const profileLink = screen.getByRole("link", { name: "My profile" });

    expect(quoteLink).toHaveAttribute("aria-current", "page");
    expect(quoteLink.className).toContain("bg-brand-50");
    expect(quoteLink.className).toContain("text-brand-700");

    expect(trackLink).not.toHaveAttribute("aria-current");
    expect(profileLink).not.toHaveAttribute("aria-current");
  });

  it("highlights My profile when on /account", () => {
    mockUsePathname.mockReturnValue("/account");
    render(<NavLink href="/account" label="My profile" className="text-sm" />);

    const profileLink = screen.getByRole("link", { name: "My profile" });
    expect(profileLink).toHaveAttribute("aria-current", "page");
    expect(profileLink.className).toContain("bg-brand-50");
  });
});
