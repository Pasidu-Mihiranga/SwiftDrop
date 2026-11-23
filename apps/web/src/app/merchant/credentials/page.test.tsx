import { describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";

const { fetchMyApplication } = vi.hoisted(() => ({ fetchMyApplication: vi.fn() }));
vi.mock("@/lib/merchant-api", () => ({ fetchMyApplication }));
vi.mock("@/components/merchant-nav", () => ({ MerchantNav: () => null }));
vi.mock("@/components/merchant-credentials-form", () => ({ MerchantCredentialsForm: () => <p>credentials form</p> }));

import MerchantCredentialsPage from "./page";

describe("MerchantCredentialsPage", () => {
  it("points a merchant without an application to the registration form", async () => {
    fetchMyApplication.mockResolvedValue(null);

    render(await MerchantCredentialsPage());

    expect(screen.getByText(/no merchant application for this account yet/)).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Register your business" })).toHaveAttribute("href", "/merchant/register");
    expect(screen.queryByText("credentials form")).not.toBeInTheDocument();
  });

  it("shows the credentials form once approved, and no registration prompt", async () => {
    fetchMyApplication.mockResolvedValue({ id: "m1", businessName: "Lanka Books", status: "APPROVED" });

    render(await MerchantCredentialsPage());

    expect(screen.getByText("credentials form")).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "Register your business" })).not.toBeInTheDocument();
  });

  it("does not claim there is no application when loading failed", async () => {
    fetchMyApplication.mockRejectedValue(new Error("down"));

    render(await MerchantCredentialsPage());

    expect(screen.getByRole("alert")).toHaveTextContent("could not load");
    expect(screen.queryByRole("link", { name: "Register your business" })).not.toBeInTheDocument();
  });
});
