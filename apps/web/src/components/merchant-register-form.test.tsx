import { beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { Merchant } from "@/lib/merchant";

const { registerMerchantMock } = vi.hoisted(() => ({ registerMerchantMock: vi.fn() }));
vi.mock("@/lib/merchant-actions", () => ({ registerMerchant: registerMerchantMock }));

import { MerchantRegisterForm } from "./merchant-register-form";

const REJECTED: Merchant = {
  id: "7e726c28-4a72-4fea-9ec1-c56b08262ddc",
  businessName: "Lanka Books",
  brNumber: "PV12345",
  contactEmail: "lankabooks@example.com",
  contactPhone: "0771234567",
  website: "https://lankabooks.example.com",
  pickupAddress: { line1: "12 Temple Road", city: "Kandy", district: "Kandy", postalCode: "20000" },
  status: "REJECTED",
  rejectionReason: "BR certificate is unreadable",
  createdAt: "2026-11-19T08:00:00Z",
};

describe("MerchantRegisterForm", () => {
  beforeEach(() => registerMerchantMock.mockReset());

  it("asks for the business and the pickup address, with the district chosen from the coverage list", () => {
    render(<MerchantRegisterForm districts={["Colombo", "Kandy"]} />);

    expect(screen.getByLabelText("Business name")).toBeRequired();
    expect(screen.getByLabelText("Business registration number")).toBeRequired();
    expect(screen.getByLabelText("Contact email")).toHaveAttribute("type", "email");
    expect(screen.getByLabelText("Website (optional)")).not.toBeRequired();
    const district = screen.getByLabelText("District");
    expect(district.tagName).toBe("SELECT");
    expect(screen.getByRole("option", { name: "Kandy" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Send application" })).toBeInTheDocument();
  });

  it("lets the district be typed when the coverage list could not be loaded", () => {
    render(<MerchantRegisterForm districts={[]} />);

    expect(screen.getByLabelText("District").tagName).toBe("INPUT");
  });

  it("fills in the earlier answers after a rejection so only the mistake needs fixing", () => {
    render(<MerchantRegisterForm districts={["Kandy"]} previous={REJECTED} />);

    expect(screen.getByLabelText("Business name")).toHaveValue("Lanka Books");
    expect(screen.getByLabelText("Business registration number")).toHaveValue("PV12345");
    expect(screen.getByLabelText("Address line 1")).toHaveValue("12 Temple Road");
    expect(screen.getByLabelText("District")).toHaveValue("Kandy");
    expect(screen.getByRole("button", { name: "Apply again" })).toBeInTheDocument();
  });

  it("shows each field message from the service next to its field", async () => {
    registerMerchantMock.mockResolvedValue({
      status: "error",
      message: "One or more fields are invalid.",
      errors: { contactPhone: "must be a Sri Lankan phone number" },
    });
    render(<MerchantRegisterForm districts={["Kandy"]} previous={REJECTED} />);

    await userEvent.click(screen.getByRole("button", { name: "Apply again" }));
    await waitFor(() => expect(registerMerchantMock).toHaveBeenCalled());

    const phone = screen.getByLabelText("Contact phone");
    expect(phone).toHaveAttribute("aria-invalid", "true");
    expect(phone).toHaveAccessibleDescription("must be a Sri Lankan phone number");
    expect(screen.getByRole("alert")).toHaveTextContent("One or more fields are invalid.");
  });

  it("sends the fields under the names merchant-service uses", async () => {
    registerMerchantMock.mockResolvedValue({ status: "ok", message: "Application sent." });
    render(<MerchantRegisterForm districts={["Kandy"]} previous={REJECTED} />);

    await userEvent.click(screen.getByRole("button", { name: "Apply again" }));
    await waitFor(() => expect(registerMerchantMock).toHaveBeenCalled());

    const sent = registerMerchantMock.mock.calls[0][1] as FormData;
    expect(sent.get("brNumber")).toBe("PV12345");
    expect(sent.get("pickupAddress.line1")).toBe("12 Temple Road");
    expect(sent.get("pickupAddress.district")).toBe("Kandy");
    expect(await screen.findByRole("status")).toHaveTextContent("Application sent.");
  });
});
