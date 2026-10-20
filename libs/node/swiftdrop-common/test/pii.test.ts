import { Writable } from "node:stream";
import { createLogger, maskName, maskPhone, maskPii } from "../src";

describe("maskPii (parity with Java PiiMasker)", () => {
  it.each([
    ["Sent receipt to nimali.perera@gmail.com", "Sent receipt to n***@gmail.com"],
    ["Call 0771234567 now", "Call 07******67 now"],
    ["Call +94771234567 now", "Call +94*******67 now"],
    ["Call +94 77 123 4567", "Call +94 ** *** **67"],
    ['{"username":"a","password":"S3cret!"}', '{"username":"a","password":"***"}'],
    ["grant_type=client_credentials&client_secret=abc123&x=1", "grant_type=client_credentials&client_secret=***&x=1"],
    ["Authorization: Bearer eyJhbGciOiJSUzI1NiJ9.payload.sig", "Authorization: Bearer ***"],
  ])("masks %s", (input, expected) => {
    expect(maskPii(input)).toBe(expected);
  });

  it("leaves dates, ids and tracking numbers alone", () => {
    const text = "Parcel SD261020ABC123 created 2026-10-20T10:15:30Z id=7f9c2ba4-e88f-4c2b-9d3a-1b2c3d4e5f60 at 1729419330000";
    expect(maskPii(text)).toBe(text);
  });

  it("is null safe", () => {
    expect(maskPii(undefined)).toBeUndefined();
    expect(maskPii("")).toBe("");
  });

  it("masks names and phones for public display", () => {
    expect(maskName("Kamal Perera")).toBe("K*** P.");
    expect(maskName("Kamal")).toBe("K***");
    expect(maskName("  ")).toBe("");
    expect(maskPhone("0771234567")).toBe("07******67");
  });
});

describe("createLogger", () => {
  function capture() {
    const lines: Record<string, unknown>[] = [];
    const stream = new Writable({
      write(chunk, _enc, done) {
        lines.push(JSON.parse(chunk.toString()));
        done();
      },
    });
    return { lines, stream };
  }

  it("writes JSON with the shared field names and masks PII in messages and objects", () => {
    const { lines, stream } = capture();
    const logger = createLogger({ service: "notification-service" }, stream);

    logger.info({ recipient: { phone: "0771234567" }, password: "x" }, "SMS sent to kamal@example.com");

    const line = lines[0];
    expect(line.service).toBe("notification-service");
    expect(line.level).toBe("INFO");
    expect(line["@timestamp"]).toEqual(expect.any(String));
    expect(line.message).toBe("SMS sent to k***@example.com");
    expect(line.recipient).toEqual({ phone: "07******67" });
    expect(line.password).toBe("***");
  });

  it("masks errors", () => {
    const { lines, stream } = capture();
    createLogger({ service: "s" }, stream).error({ err: new Error("bad phone 0771234567") }, "failed");
    expect(JSON.stringify(lines[0])).not.toContain("0771234567");
  });
});
