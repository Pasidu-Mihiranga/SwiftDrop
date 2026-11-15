import { redirect } from "next/navigation";

/** Pricing administration starts at the zone map, which everything else is keyed on. */
export default function PricingAdminHome() {
  redirect("/admin/pricing/zones");
}
