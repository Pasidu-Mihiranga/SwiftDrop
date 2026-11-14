import { beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

const { closeRateCardMock } = vi.hoisted(() => ({ closeRateCardMock: vi.fn() }));
vi.mock("@/lib/pricing-actions", () => ({ closeRateCard: closeRateCardMock }));

import { CloseRateForm } from "./close-rate-form";

describe("CloseRateForm", () => {
  beforeEach(() => closeRateCardMock.mockReset());

  it("offers nothing for a card that was already closed", () => {
    const { container } = render(<CloseRateForm rateCardId="1" effectiveFrom="2026-01-01" closed />);

    expect(container).toBeEmptyDOMElement();
  });

  it("keeps the result on screen after closing removes the form", async () => {
    closeRateCardMock.mockResolvedValue({ status: "ok", message: "Rate closed from 2026-06-01." });
    const { container, rerender } = render(<CloseRateForm rateCardId="1" effectiveFrom="2026-01-01" closed={false} />);

    await userEvent.click(screen.getByText("Close this rate"));
    const date = container.querySelector<HTMLInputElement>('input[name="effectiveTo"]')!;
    await userEvent.type(date, "2026-06-01");
    await userEvent.click(screen.getByRole("button", { name: "Close rate" }));
    await waitFor(() => expect(closeRateCardMock).toHaveBeenCalled());

    // The page revalidates and the card comes back closed, exactly as the server would send it.
    rerender(<CloseRateForm rateCardId="1" effectiveFrom="2026-01-01" closed />);

    expect(await screen.findByRole("status")).toHaveTextContent("Rate closed from 2026-06-01.");
    expect(screen.queryByRole("button", { name: "Close rate" })).toBeNull();
  });
});
