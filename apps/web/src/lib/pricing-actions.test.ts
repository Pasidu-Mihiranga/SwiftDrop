import { beforeEach, describe, expect, it, vi } from "vitest";

const { authMock, gatewayFetchMock, revalidatePathMock } = vi.hoisted(() => ({
  authMock: vi.fn(),
  gatewayFetchMock: vi.fn(),
  revalidatePathMock: vi.fn(),
}));

vi.mock("@/auth", () => ({ auth: authMock }));
vi.mock("@/lib/gateway", () => ({ gatewayFetch: gatewayFetchMock }));
vi.mock("next/cache", () => ({ revalidatePath: revalidatePathMock }));

import { updateSizeClass } from "@/lib/pricing-actions";

function sizeForm(): FormData {
  const form = new FormData();
  form.set("code", "M");
  form.set("multiplier", "1.25");
  return form;
}

describe("admin pricing actions", () => {
  beforeEach(() => {
    vi.resetAllMocks();
  });

  it("rejects a non-admin session before contacting the gateway", async () => {
    authMock.mockResolvedValue({ user: { roles: ["DISPATCHER"] } });

    const result = await updateSizeClass({ status: "idle" }, sizeForm());

    expect(result).toEqual({ status: "error", message: "You do not have permission to change pricing." });
    expect(gatewayFetchMock).not.toHaveBeenCalled();
  });

  it("sends an admin's update and refreshes pricing views", async () => {
    authMock.mockResolvedValue({ user: { roles: ["ADMIN"] } });
    gatewayFetchMock.mockResolvedValue(new Response(null, { status: 204 }));

    const result = await updateSizeClass({ status: "idle" }, sizeForm());

    expect(result).toEqual({ status: "ok", message: "Size M updated." });
    expect(gatewayFetchMock).toHaveBeenCalledWith(
      "/api/v1/admin/pricing/size-classes/M",
      expect.objectContaining({ method: "PUT", body: JSON.stringify({ multiplier: 1.25 }) }),
    );
    expect(revalidatePathMock).toHaveBeenCalledWith("/admin/pricing/zones");
    expect(revalidatePathMock).toHaveBeenCalledWith("/admin/pricing/rate-cards");
  });
});
