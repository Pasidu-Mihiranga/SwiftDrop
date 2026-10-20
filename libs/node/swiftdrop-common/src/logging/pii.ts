/**
 * Masks personal data and credentials before text reaches a log sink.
 * Same rules as the Java PiiMasker (libs/java/swiftdrop-common-logging) — keep them in sync.
 */
type Rule = [RegExp, (match: string, ...groups: string[]) => string];

const RULES: Rule[] = [
  // "password":"secret" / password=secret (also secret, client_secret, api key and token variants)
  [
    /("?(?:password|passwd|secret|client_secret|api[_-]?key|access_token|refresh_token)"?\s*[:=]\s*"?)([^"\s,&}]+)/gi,
    (_m, prefix) => `${prefix}***`,
  ],
  // Authorization: Bearer eyJ...
  [/(bearer\s+)[A-Za-z0-9._~+/=-]+/gi, (_m, prefix) => `${prefix}***`],
  // jane.doe@example.com -> j***@example.com
  [/([A-Za-z0-9._%+-])[A-Za-z0-9._%+-]*@([A-Za-z0-9.-]+\.[A-Za-z]{2,})/g, (_m, first, domain) => `${first}***@${domain}`],
  // +94771234567, 0771234567, +94 77 123 4567
  [
    /(?<![\w+])(?:\+\d{1,3}[ -]?\d{2,4}[ -]?\d{3,4}[ -]?\d{3,4}|0\d{9})(?!\w)/g,
    (match) => maskDigits(match),
  ],
];

/** Keeps the first two and last two digits: +94771234567 -> +94*******67. */
function maskDigits(phone: string): string {
  const total = phone.replace(/\D/g, "").length;
  let seen = 0;
  return phone.replace(/\d/g, (digit) => {
    seen++;
    return seen > 2 && seen <= total - 2 ? "*" : digit;
  });
}

export function maskPii(text: string): string;
export function maskPii(text: string | null | undefined): string | null | undefined;
export function maskPii(text: string | null | undefined): string | null | undefined {
  if (!text) return text;
  return RULES.reduce((acc, [pattern, replace]) => acc.replace(pattern, replace as never), text);
}

/** Masks a phone number for display, e.g. on the public tracking page: 0771234567 -> 07******67. */
export function maskPhone(phone: string): string {
  return maskDigits(phone);
}

/** Masks a person's name for public display: "Kamal Perera" -> "K*** P." */
export function maskName(name: string): string {
  const parts = name.trim().split(/\s+/).filter(Boolean);
  if (parts.length === 0) return "";
  const [first, ...rest] = parts;
  const last = rest.at(-1);
  return `${first[0]}***${last ? ` ${last[0]}.` : ""}`;
}
