import { beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

const { approveMock, rejectMock, suspendMock } = vi.hoisted(() => ({
  approveMock: vi.fn(),
  rejectMock: vi.fn(),
  suspendMock: vi.fn(),
}));
vi.mock("@/lib/merchant-actions", () => ({
  approveMerchant: approveMock,
  rejectMerchant: rejectMock,
  suspendMerchant: suspendMock,
}));

import { MerchantDecisions } from "./merchant-decision-forms";

const ID = "7e726c28-4a72-4fea-9ec1-c56b08262ddc";

describe("MerchantDecisions", () => {
  beforeEach(() => {
    approveMock.mockReset();
    rejectMock.mockReset();
    suspendMock.mockReset();
  });

  it("offers approve and reject for a pending application, but not suspend", () => {
    render(<MerchantDecisions merchantId={ID} businessName="Lanka Books" status="PENDING" />);

    expect(screen.getByRole("button", { name: "Approve" })).toBeInTheDocument();
    expect(screen.getByText("Reject")).toBeInTheDocument();
    expect(screen.queryByText("Suspend")).toBeNull();
  });

  it("offers only suspend for an approved merchant", () => {
    render(<MerchantDecisions merchantId={ID} businessName="Lanka Books" status="APPROVED" />);

    expect(screen.getByText("Suspend")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Approve" })).toBeNull();
  });

  it.each(["REJECTED", "SUSPENDED"] as const)("offers nothing once an application is %s", (status) => {
    const { container } = render(<MerchantDecisions merchantId={ID} businessName="Lanka Books" status={status} />);

    expect(container).toBeEmptyDOMElement();
  });

  it("approves the right application", async () => {
    approveMock.mockResolvedValue({ status: "ok", message: "Approved." });
    render(<MerchantDecisions merchantId={ID} businessName="Lanka Books" status="PENDING" />);

    await userEvent.click(screen.getByRole("button", { name: "Approve" }));
    await waitFor(() => expect(approveMock).toHaveBeenCalled());

    expect((approveMock.mock.calls[0][1] as FormData).get("merchantId")).toBe(ID);
    expect(await screen.findByRole("status")).toHaveTextContent("Approved.");
  });

  it("sends the reason the admin typed and shows a refusal from the service", async () => {
    rejectMock.mockResolvedValue({ status: "error", message: "That decision is no longer possible." });
    render(<MerchantDecisions merchantId={ID} businessName="Lanka Books" status="PENDING" />);

    await userEvent.click(screen.getByText("Reject"));
    const reason = screen.getByLabelText("Reason the applicant will read");
    expect(reason).toHaveAttribute("maxlength", "500");
    await userEvent.type(reason, "BR certificate is unreadable");
    await userEvent.click(screen.getByRole("button", { name: "Reject application" }));
    await waitFor(() => expect(rejectMock).toHaveBeenCalled());

    expect((rejectMock.mock.calls[0][1] as FormData).get("reason")).toBe("BR certificate is unreadable");
    expect(await screen.findByRole("alert")).toHaveTextContent("That decision is no longer possible.");
  });

  it("asks for a confirmation before suspending", async () => {
    suspendMock.mockResolvedValue({ status: "ok", message: "Suspended." });
    render(<MerchantDecisions merchantId={ID} businessName="Lanka Books" status="APPROVED" />);

    await userEvent.click(screen.getByText("Suspend"));
    expect(screen.getByRole("form", { name: "Suspend Lanka Books" })).toBeInTheDocument();
    const confirm = screen.getByLabelText("Yes, stop Lanka Books's API access");
    expect(confirm).toBeRequired();
    await userEvent.click(confirm);
    await userEvent.click(screen.getByRole("button", { name: "Suspend merchant" }));
    await waitFor(() => expect(suspendMock).toHaveBeenCalled());

    expect((suspendMock.mock.calls[0][1] as FormData).get("confirm")).toBe("on");
  });
});
