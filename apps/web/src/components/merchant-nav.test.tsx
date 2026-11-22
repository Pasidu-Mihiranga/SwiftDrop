import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import { MerchantNav } from "./merchant-nav";

describe("MerchantNav", () => {
  it("links the portal's pages and marks the current one", () => {
    render(<MerchantNav current="/merchant/orders" />);

    expect(screen.getByRole("link", { name: "Overview" })).toHaveAttribute("href", "/merchant");
    expect(screen.getByRole("link", { name: "API credentials" })).toHaveAttribute("href", "/merchant/credentials");
    const orders = screen.getByRole("link", { name: "Orders" });
    expect(orders).toHaveAttribute("aria-current", "page");
    expect(screen.getByRole("link", { name: "Overview" })).not.toHaveAttribute("aria-current");
  });
});
