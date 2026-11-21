import { beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

const { rotateCredentialsMock } = vi.hoisted(() => ({ rotateCredentialsMock: vi.fn() }));
vi.mock("@/lib/merchant-actions", () => ({ rotateCredentials: rotateCredentialsMock }));

import { MerchantCredentialsForm } from "./merchant-credentials-form";

const CREDENTIALS = {
  clientId: "merchant-7e726c28-4a72-4fea-9ec1-c56b08262ddc",
  clientSecret: "s3cr3t-value",
  tokenUrl: "http://localhost:8180/realms/swiftdrop/protocol/openid-connect/token",
};

async function generate() {
  await userEvent.click(screen.getByLabelText(/current secret stops working/));
  await userEvent.click(screen.getByRole("button", { name: "Generate new secret" }));
}

describe("MerchantCredentialsForm", () => {
  beforeEach(() => rotateCredentialsMock.mockReset());

  it("asks the merchant to confirm before a new secret replaces the old one", () => {
    render(<MerchantCredentialsForm />);

    expect(screen.getByLabelText(/current secret stops working/)).toBeRequired();
    expect(screen.queryByLabelText("Client secret")).not.toBeInTheDocument();
  });

  it("shows the new credentials once, ready to copy", async () => {
    rotateCredentialsMock.mockResolvedValue({ status: "ok", credentials: CREDENTIALS });
    render(<MerchantCredentialsForm />);

    await generate();

    expect(await screen.findByLabelText("Client secret")).toHaveValue("s3cr3t-value");
    expect(screen.getByLabelText("Client ID")).toHaveValue(CREDENTIALS.clientId);
    expect(screen.getByLabelText("Token URL")).toHaveValue(CREDENTIALS.tokenUrl);
    expect(screen.getByText(/shown only once/)).toBeInTheDocument();
    expect(rotateCredentialsMock.mock.calls[0][1].get("confirm")).toBe("on");
  });

  it("copies a value to the clipboard", async () => {
    rotateCredentialsMock.mockResolvedValue({ status: "ok", credentials: CREDENTIALS });
    // One session for the whole test, so user-event's clipboard stub can be read back.
    const user = userEvent.setup();
    render(<MerchantCredentialsForm />);
    await user.click(screen.getByLabelText(/current secret stops working/));
    await user.click(screen.getByRole("button", { name: "Generate new secret" }));

    await user.click(await screen.findByRole("button", { name: "Copy Client secret" }));

    expect(await navigator.clipboard.readText()).toBe("s3cr3t-value");
    expect(screen.getByRole("button", { name: "Copy Client secret" })).toHaveTextContent("Copied");
  });

  it("hides the secret for good once the merchant has saved it", async () => {
    rotateCredentialsMock.mockResolvedValue({ status: "ok", credentials: CREDENTIALS });
    render(<MerchantCredentialsForm />);
    await generate();

    await userEvent.click(await screen.findByRole("button", { name: /hide the secret/ }));

    expect(screen.queryByDisplayValue("s3cr3t-value")).not.toBeInTheDocument();
    expect(screen.getByText(/cannot be shown again/)).toBeInTheDocument();
  });

  it("shows why no secret was issued", async () => {
    rotateCredentialsMock.mockResolvedValue({ status: "error", message: "Your merchant account is not active." });
    render(<MerchantCredentialsForm />);

    await generate();

    await waitFor(() => expect(screen.getByRole("alert")).toHaveTextContent("Your merchant account is not active."));
    expect(screen.queryByLabelText("Client secret")).not.toBeInTheDocument();
  });
});
