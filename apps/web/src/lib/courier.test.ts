import { describe, expect, it, vi } from "vitest";
import {
  CourierError,
  type ParcelStatus,
  type UploadRequest,
  loadParcel,
  nextSteps,
  proofFileProblem,
  requestProofUpload,
  statusUpdateBody,
  stepState,
  updateStatus,
  uploadProof,
} from "./courier";

function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: { "content-type": "application/json" } });
}

describe("nextSteps", () => {
  it.each<[ParcelStatus, ParcelStatus[]]>([
    ["CREATED", ["PICKED_UP"]],
    ["PICKED_UP", ["IN_TRANSIT"]],
    ["IN_TRANSIT", ["OUT_FOR_DELIVERY"]],
    ["OUT_FOR_DELIVERY", ["DELIVERED", "FAILED"]],
    ["FAILED", []],
    ["DELIVERED", []],
    ["CANCELLED", []],
  ])("from %s offers %j", (from, expected) => {
    expect(nextSteps(from)).toEqual(expected);
  });
});

describe("stepState", () => {
  it("marks earlier steps done and the current one current", () => {
    expect(stepState("CREATED", "IN_TRANSIT")).toBe("done");
    expect(stepState("IN_TRANSIT", "IN_TRANSIT")).toBe("current");
    expect(stepState("DELIVERED", "IN_TRANSIT")).toBe("todo");
  });

  it("treats a delivered parcel as complete and a failure as stuck at out for delivery", () => {
    expect(stepState("DELIVERED", "DELIVERED")).toBe("done");
    expect(stepState("OUT_FOR_DELIVERY", "FAILED")).toBe("current");
    expect(stepState("DELIVERED", "FAILED")).toBe("todo");
    expect(stepState("CREATED", "CANCELLED")).toBe("current");
  });
});

describe("proofFileProblem", () => {
  it("accepts JPEG and PNG up to 5 MB", () => {
    expect(proofFileProblem({ type: "image/jpeg", size: 1000 })).toBeNull();
    expect(proofFileProblem({ type: "image/png", size: 5 * 1024 * 1024 })).toBeNull();
  });

  it("explains anything else", () => {
    expect(proofFileProblem({ type: "image/heic", size: 1000 })).toMatch(/JPEG or PNG/);
    expect(proofFileProblem({ type: "image/png", size: 0 })).toMatch(/empty/);
    expect(proofFileProblem({ type: "image/png", size: 5 * 1024 * 1024 + 1 })).toMatch(/5 MB/);
  });
});

describe("statusUpdateBody", () => {
  it("sends a plain step with an optional note", () => {
    expect(statusUpdateBody("PICKED_UP")).toEqual({ body: { toStatus: "PICKED_UP", note: undefined } });
    expect(statusUpdateBody("IN_TRANSIT", { note: "  on the way " })).toEqual({ body: { toStatus: "IN_TRANSIT", note: "on the way" } });
  });

  it("needs a reason for a failure, and a note for OTHER", () => {
    expect(statusUpdateBody("FAILED", { failure: { reason: "", note: "" } }).error).toMatch(/why/);
    expect(statusUpdateBody("FAILED", { failure: { reason: "OTHER", note: " " } }).error).toMatch(/note/);
    expect(statusUpdateBody("FAILED", { failure: { reason: "REFUSED", note: "" } })).toEqual({
      body: { toStatus: "FAILED", failureReason: "REFUSED", note: undefined },
    });
  });

  it("needs a name or a photo for a delivery, preferring the photo", () => {
    expect(statusUpdateBody("DELIVERED", { delivery: { receivedBy: " ", proofId: null } }).error).toMatch(/received/);
    expect(statusUpdateBody("DELIVERED", { delivery: { receivedBy: "Saman", proofId: null } }).body).toEqual({
      toStatus: "DELIVERED", note: undefined, proof: { type: "NAME", receivedBy: "Saman" },
    });
    expect(statusUpdateBody("DELIVERED", { delivery: { receivedBy: "Saman", proofId: "p-1" } }).body).toEqual({
      toStatus: "DELIVERED", note: undefined, proof: { type: "PHOTO", proofId: "p-1", receivedBy: "Saman" },
    });
    expect(statusUpdateBody("DELIVERED").error).toBeDefined();
  });
});

describe("API calls", () => {
  it("loads, updates and asks for an upload link through the BFF", async () => {
    const fetchImpl = vi.fn(async () => json({ id: "p-1", trackingNumber: "SD1", status: "PICKED_UP" }));

    await loadParcel("p-1", fetchImpl);
    await updateStatus("p-1", { toStatus: "PICKED_UP" }, fetchImpl);
    await requestProofUpload("p-1", { type: "image/png", size: 12 }, fetchImpl);

    const calls = fetchImpl.mock.calls as unknown as [string, RequestInit][];
    expect(calls[0][0]).toBe("/api/bff/api/v1/parcels/p-1");
    expect(calls[1][0]).toBe("/api/bff/api/v1/courier/parcels/p-1/status");
    expect(calls[1][1].method).toBe("PATCH");
    expect(calls[2][0]).toBe("/api/bff/api/v1/courier/parcels/p-1/proof-upload-url");
    expect(JSON.parse(String(calls[2][1].body))).toEqual({ contentType: "image/png", sizeBytes: 12 });
  });

  it.each([
    [json({ code: "INVALID_STATUS_TRANSITION", detail: "x" }, 422), "INVALID_STATUS_TRANSITION", /not possible/],
    [new Response("", { status: 404 }), "NOT_FOUND", /not assigned to you/],
    [json({ code: "SOMETHING_NEW", detail: "Server says so" }, 422), "SOMETHING_NEW", /Server says so/],
    [new Response("oops", { status: 500 }), "UNEXPECTED", /went wrong/],
  ])("turns a refusal into a courier-friendly message", async (response, code, message) => {
    const error = await updateStatus("p-1", {}, vi.fn(async () => response)).catch((e: unknown) => e);
    expect(error).toBeInstanceOf(CourierError);
    expect((error as CourierError).code).toBe(code);
    expect((error as CourierError).message).toMatch(message);
  });

  it("explains a lost connection", async () => {
    const error = await loadParcel("p-1", vi.fn(async () => { throw new TypeError("offline"); })).catch((e: unknown) => e);
    expect((error as CourierError).code).toBe("UNREACHABLE");
  });
});

describe("uploadProof", () => {
  function fakeRequest(status: number, fail = false) {
    const request: UploadRequest & { headers: Record<string, string>; method?: string; url?: string } = {
      upload: { onprogress: null },
      onload: null,
      onerror: null,
      status,
      headers: {},
      open(method, url) { this.method = method; this.url = url; },
      setRequestHeader(name, value) { this.headers[name] = value; },
      send() {
        this.upload.onprogress?.({ loaded: 50, total: 100, lengthComputable: true });
        if (fail) this.onerror?.();
        else this.onload?.();
      },
    };
    return request;
  }

  it("PUTs the file with its type and reports progress", async () => {
    const request = fakeRequest(200);
    const progress: number[] = [];
    await uploadProof("https://s3/put", new Blob(["x"], { type: "image/png" }), (p) => progress.push(p), () => request);

    expect(request.method).toBe("PUT");
    expect(request.url).toBe("https://s3/put");
    expect(request.headers["Content-Type"]).toBe("image/png");
    expect(progress).toEqual([50, 100]);
  });

  it("fails clearly when storage refuses or the network drops", async () => {
    await expect(uploadProof("u", new Blob(["x"], { type: "image/png" }), () => {}, () => fakeRequest(403)))
      .rejects.toMatchObject({ code: "UPLOAD_FAILED" });
    await expect(uploadProof("u", new Blob(["x"], { type: "image/png" }), () => {}, () => fakeRequest(0, true)))
      .rejects.toMatchObject({ code: "UPLOAD_FAILED" });
  });
});
