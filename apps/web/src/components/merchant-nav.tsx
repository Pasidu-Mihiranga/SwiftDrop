import Link from "next/link";

/** The merchant portal's own pages (M7). */
export const MERCHANT_LINKS = [
  { href: "/merchant", label: "Overview" },
  { href: "/merchant/orders", label: "Orders" },
  { href: "/merchant/credentials", label: "API credentials" },
] as const;

export function MerchantNav({ current }: { current: (typeof MERCHANT_LINKS)[number]["href"] }) {
  return (
    <nav aria-label="Merchant portal" className="flex flex-wrap gap-1 text-sm">
      {MERCHANT_LINKS.map((link) => (
        <Link
          key={link.href}
          href={link.href}
          aria-current={link.href === current ? "page" : undefined}
          className={link.href === current ? "btn-primary" : "btn-secondary"}
        >
          {link.label}
        </Link>
      ))}
    </nav>
  );
}
