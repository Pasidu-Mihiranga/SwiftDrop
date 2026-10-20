import Ajv2020, { type ValidateFunction } from "ajv/dist/2020";
import addFormats from "ajv-formats";
import { existsSync, readdirSync, readFileSync } from "node:fs";
import { join } from "node:path";

/** Contract file names without extension, e.g. "parcel-status-changed.v1". */
export type ContractName =
  | "parcel-created.v1"
  | "parcel-status-changed.v1"
  | "parcel-assigned.v1"
  | "merchant-lifecycle.v1"
  | "webhook-dead-letter.v1";

const BASE_ID = "https://swiftdrop.lk/contracts/events/";

/**
 * The JSON Schemas are copied into dist/contracts at build time; in tests (ts-jest) they are read
 * straight from the repository's /contracts/events folder.
 */
function contractsDir(): string {
  const candidates = [join(__dirname, "..", "contracts"), join(__dirname, "..", "..", "..", "..", "..", "contracts", "events")];
  const found = candidates.find((dir) => existsSync(join(dir, "envelope.schema.json")));
  if (!found) throw new Error(`Event contracts not found (looked in ${candidates.join(", ")})`);
  return found;
}

let ajv: Ajv2020 | undefined;
const validators = new Map<ContractName, ValidateFunction>();

function compiler(): Ajv2020 {
  if (!ajv) {
    ajv = new Ajv2020({ allErrors: true, strict: false });
    addFormats(ajv);
    const dir = contractsDir();
    for (const file of readdirSync(dir).filter((f) => f.endsWith(".schema.json"))) {
      ajv.addSchema(JSON.parse(readFileSync(join(dir, file), "utf8")));
    }
  }
  return ajv;
}

export interface ValidationResult {
  valid: boolean;
  errors: string[];
}

/** Validates a complete event (envelope + data) against its contract. */
export function validateEvent(contract: ContractName, event: unknown): ValidationResult {
  let validate = validators.get(contract);
  if (!validate) {
    validate = compiler().getSchema(`${BASE_ID}${contract}.schema.json`);
    if (!validate) throw new Error(`Unknown event contract ${contract}`);
    validators.set(contract, validate);
  }
  const valid = validate(event) as boolean;
  return {
    valid,
    errors: valid ? [] : (validate.errors ?? []).map((e) => `${e.instancePath || "/"} ${e.message ?? "invalid"}`),
  };
}

/** Absolute path of the bundled contract example for a contract (useful in tests). */
export function contractExample(contract: ContractName): unknown {
  return JSON.parse(readFileSync(join(contractsDir(), "examples", `${contract}.example.json`), "utf8"));
}
