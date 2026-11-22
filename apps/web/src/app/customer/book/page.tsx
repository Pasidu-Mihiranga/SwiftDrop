import { BookDeliveryWizard } from "./book-delivery-wizard";
import { mapConfig } from "@/lib/map-config";
import { quoteFromSearchParams } from "@/lib/pricing";
import { fetchActiveZones, fetchSpecialHandling } from "@/lib/pricing-api";

export const metadata = {
  title: "Book a Delivery - SwiftDrop",
  description: "Create a delivery request, review your official fee quote, and get an instant tracking number.",
};

export default async function BookDeliveryPage({
  searchParams,
}: {
  searchParams: Promise<Record<string, string | string[] | undefined>>;
}) {
  const [{ zones, error }, { options: specialHandling }, resolvedParams] = await Promise.all([
    fetchActiveZones(),
    fetchSpecialHandling(),
    searchParams,
  ]);

  const initialPricing = quoteFromSearchParams(resolvedParams);

  return (
    <BookDeliveryWizard
      zones={zones}
      specialHandling={specialHandling}
      coverageError={error}
      map={mapConfig()}
      initialPricing={initialPricing}
    />
  );
}
