"use client";

import { PricingForm } from "@/components/pricing-form";
import type { Merchant } from "@/lib/merchant";
import { registerMerchant } from "@/lib/merchant-actions";
import type { ActionState } from "@/lib/pricing-actions";

interface Field {
  name: string;
  label: string;
  type?: string;
  required?: boolean;
  autoComplete?: string;
  hint?: string;
  value: (merchant: Merchant) => string | undefined;
}

const BUSINESS_FIELDS: Field[] = [
  { name: "businessName", label: "Business name", required: true, autoComplete: "organization", value: (m) => m.businessName },
  {
    name: "brNumber",
    label: "Business registration number",
    required: true,
    hint: "As printed on your BR certificate, for example PV 12345.",
    value: (m) => m.brNumber,
  },
  { name: "contactEmail", label: "Contact email", type: "email", required: true, autoComplete: "email", value: (m) => m.contactEmail },
  {
    name: "contactPhone",
    label: "Contact phone",
    type: "tel",
    required: true,
    autoComplete: "tel",
    hint: "A Sri Lankan number, for example 0771234567.",
    value: (m) => m.contactPhone,
  },
  { name: "website", label: "Website (optional)", type: "url", autoComplete: "url", value: (m) => m.website },
];

const ADDRESS_FIELDS: Field[] = [
  { name: "pickupAddress.line1", label: "Address line 1", required: true, autoComplete: "address-line1", value: (m) => m.pickupAddress.line1 },
  { name: "pickupAddress.line2", label: "Address line 2 (optional)", autoComplete: "address-line2", value: (m) => m.pickupAddress.line2 },
  { name: "pickupAddress.city", label: "City", required: true, autoComplete: "address-level2", value: (m) => m.pickupAddress.city },
  {
    name: "pickupAddress.postalCode",
    label: "Postal code (optional)",
    autoComplete: "postal-code",
    value: (m) => m.pickupAddress.postalCode,
  },
];

/**
 * The merchant application. After a rejection it opens with the earlier answers filled in, so the
 * applicant only corrects what the admin asked about.
 *
 * @param districts from pricing-service's coverage; when that list is unavailable the district is typed
 */
export function MerchantRegisterForm({ districts, previous }: { districts: string[]; previous?: Merchant }) {
  return (
    <PricingForm action={registerMerchant} submitLabel={previous ? "Apply again" : "Send application"}>
      {(state: ActionState) => (
        <>
          <fieldset className="space-y-3">
            <legend className="font-semibold">Your business</legend>
            {BUSINESS_FIELDS.map((field) => (
              <TextField key={field.name} field={field} previous={previous} error={state.errors?.[field.name]} />
            ))}
          </fieldset>

          <fieldset className="space-y-3">
            <legend className="font-semibold">Where couriers collect your parcels</legend>
            {ADDRESS_FIELDS.slice(0, 3).map((field) => (
              <TextField key={field.name} field={field} previous={previous} error={state.errors?.[field.name]} />
            ))}
            <DistrictField districts={districts} previous={previous} error={state.errors?.["pickupAddress.district"]} />
            <TextField field={ADDRESS_FIELDS[3]} previous={previous} error={state.errors?.[ADDRESS_FIELDS[3].name]} />
          </fieldset>
        </>
      )}
    </PricingForm>
  );
}

function TextField({ field, previous, error }: { field: Field; previous?: Merchant; error?: string }) {
  const id = fieldId(field.name);
  return (
    <span className="block">
      <label className="block text-sm font-medium text-slate-700" htmlFor={id}>
        {field.label}
      </label>
      <input
        id={id}
        name={field.name}
        type={field.type ?? "text"}
        className="input"
        required={field.required}
        autoComplete={field.autoComplete}
        defaultValue={previous ? field.value(previous) ?? "" : ""}
        aria-invalid={error ? true : undefined}
        aria-describedby={error ? `${id}-error` : field.hint ? `${id}-hint` : undefined}
      />
      {field.hint && !error ? (
        <span id={`${id}-hint`} className="text-xs text-slate-500">
          {field.hint}
        </span>
      ) : null}
      {error ? (
        <span id={`${id}-error`} className="field-error">
          {error}
        </span>
      ) : null}
    </span>
  );
}

function DistrictField({ districts, previous, error }: { districts: string[]; previous?: Merchant; error?: string }) {
  const id = fieldId("pickupAddress.district");
  const current = previous?.pickupAddress.district ?? "";
  const common = {
    id,
    name: "pickupAddress.district",
    className: "input",
    required: true,
    defaultValue: current,
    "aria-invalid": error ? true : undefined,
    "aria-describedby": error ? `${id}-error` : undefined,
  } as const;

  return (
    <span className="block">
      <label className="block text-sm font-medium text-slate-700" htmlFor={id}>
        District
      </label>
      {districts.length > 0 ? (
        <select {...common}>
          <option value="">Select a district</option>
          {districts.map((district) => (
            <option key={district} value={district}>
              {district}
            </option>
          ))}
        </select>
      ) : (
        <input {...common} type="text" autoComplete="address-level1" />
      )}
      {error ? (
        <span id={`${id}-error`} className="field-error">
          {error}
        </span>
      ) : null}
    </span>
  );
}

function fieldId(name: string): string {
  return `merchant-${name.replace(".", "-")}`;
}
