import { businessToday, resolveFilter } from "@/lib/reports";
import { loadReports } from "./data";
import { ReportsView } from "./reports-view";

export const metadata = { title: "Reports" };

export default async function ReportsPage({
  searchParams,
}: {
  searchParams: Promise<Record<string, string | string[] | undefined>>;
}) {
  const { filter, notice } = resolveFilter(await searchParams, businessToday());
  return <ReportsView filter={filter} notice={notice} data={await loadReports(filter)} />;
}
