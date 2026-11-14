import { describe, expect, it, vi } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { PricingForm } from "./pricing-form";
import type { ActionState } from "@/lib/pricing-actions";

/** An action that resolves on demand, so the pending state can be observed. */
function deferredAction() {
  let release: (state: ActionState) => void = () => {};
  const action = vi.fn(
    () =>
      new Promise<ActionState>((resolve) => {
        release = resolve;
      }),
  );
  return { action, release: (state: ActionState) => release(state) };
}

describe("PricingForm", () => {
  it("renders the submit label and nothing else before submission", () => {
    render(
      <PricingForm action={async () => ({ status: "ok" })} submitLabel="Save zone" />,
    );

    expect(screen.getByRole("button", { name: "Save zone" })).toBeEnabled();
    // An idle action must not announce anything, or a screen reader hears it on load.
    expect(screen.queryByRole("status")).toBeNull();
    expect(screen.queryByRole("alert")).toBeNull();
  });

  it("renders static children", () => {
    render(
      <PricingForm action={async () => ({ status: "ok" })} submitLabel="Save">
        <label htmlFor="zoneCode">Zone</label>
      </PricingForm>,
    );

    expect(screen.getByText("Zone")).toBeInTheDocument();
  });

  it("passes the action state to a render-prop child", async () => {
    render(
      <PricingForm
        action={async () => ({ status: "error", message: "Nope." })}
        submitLabel="Save"
      >
        {(state) => <p>state: {state.status}</p>}
      </PricingForm>,
    );

    expect(screen.getByText("state: idle")).toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: "Save" }));

    await waitFor(() => expect(screen.getByText("state: error")).toBeInTheDocument());
  });

  it("submits the form data to the action", async () => {
    const action = vi.fn<(previous: ActionState, formData: FormData) => Promise<ActionState>>(
      async () => ({ status: "ok" }),
    );
    render(
      <PricingForm action={action} submitLabel="Assign">
        <input defaultValue="Colombo" name="district" />
      </PricingForm>,
    );

    await userEvent.click(screen.getByRole("button", { name: "Assign" }));

    await waitFor(() => expect(action).toHaveBeenCalledTimes(1));
    expect(action.mock.calls[0][1].get("district")).toBe("Colombo");
  });

  it("disables the button and shows progress while the action is in flight", async () => {
    const { action, release } = deferredAction();
    render(<PricingForm action={action} submitLabel="Save" />);

    await userEvent.click(screen.getByRole("button", { name: "Save" }));

    const pending = await screen.findByRole("button", { name: "Saving..." });
    expect(pending).toBeDisabled();

    release({ status: "ok", message: "Saved." });

    await waitFor(() => expect(screen.getByRole("button", { name: "Save" })).toBeEnabled());
  });

  it("announces success politely, as a status rather than an alert", async () => {
    render(
      <PricingForm
        action={async () => ({ status: "ok", message: "Western updated." })}
        submitLabel="Save"
      />,
    );

    await userEvent.click(screen.getByRole("button", { name: "Save" }));

    const status = await screen.findByRole("status");
    expect(status).toHaveTextContent("Western updated.");
    expect(status).toHaveClass("notice-success");
    expect(screen.queryByRole("alert")).toBeNull();
  });

  it("announces failure as an alert so it is not missed", async () => {
    render(
      <PricingForm
        action={async () => ({ status: "error", message: "The lane already has a card." })}
        submitLabel="Save"
      />,
    );

    await userEvent.click(screen.getByRole("button", { name: "Save" }));

    const alert = await screen.findByRole("alert");
    expect(alert).toHaveTextContent("The lane already has a card.");
    expect(alert).toHaveClass("notice-error");
  });

  it("falls back to a default message when the action supplies none", async () => {
    render(<PricingForm action={async () => ({ status: "ok" })} submitLabel="Save" />);
    await userEvent.click(screen.getByRole("button", { name: "Save" }));
    expect(await screen.findByRole("status")).toHaveTextContent("Saved.");
  });

  it("falls back to a default failure message too", async () => {
    render(<PricingForm action={async () => ({ status: "error" })} submitLabel="Save" />);
    await userEvent.click(screen.getByRole("button", { name: "Save" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("That did not work.");
  });

  it("lists field-level validation messages from problem+json", async () => {
    render(
      <PricingForm
        action={async () => ({
          status: "error",
          message: "Check the fields.",
          errors: { baseFee: "must not be negative", effectiveFrom: "is required" },
        })}
        submitLabel="Create"
      />,
    );

    await userEvent.click(screen.getByRole("button", { name: "Create" }));

    const alert = await screen.findByRole("alert");
    const items = await screen.findAllByRole("listitem");
    expect(items).toHaveLength(2);
    expect(alert).toHaveTextContent("baseFee: must not be negative");
    expect(alert).toHaveTextContent("effectiveFrom: is required");
  });

  it("omits the field list when there are no field errors", async () => {
    render(
      <PricingForm
        action={async () => ({ status: "error", message: "Service unavailable." })}
        submitLabel="Save"
      />,
    );

    await userEvent.click(screen.getByRole("button", { name: "Save" }));

    await screen.findByRole("alert");
    expect(screen.queryByRole("listitem")).toBeNull();
  });

  it("applies the default layout class and allows overriding it", () => {
    const { container, unmount } = render(
      <PricingForm action={async () => ({ status: "ok" })} submitLabel="Save" />,
    );
    expect(container.querySelector("form")).toHaveClass("space-y-3");
    unmount();

    const { container: custom } = render(
      <PricingForm
        action={async () => ({ status: "ok" })}
        submitLabel="Save"
        className="grid gap-2"
      />,
    );
    expect(custom.querySelector("form")).toHaveClass("grid", "gap-2");
  });
});
