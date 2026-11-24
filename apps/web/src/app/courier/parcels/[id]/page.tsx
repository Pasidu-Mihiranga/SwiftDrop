import { ParcelUpdate } from "@/components/courier/parcel-update";

export const metadata = { title: "Update parcel" };

export default async function CourierParcelPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  return <ParcelUpdate parcelId={id} />;
}
