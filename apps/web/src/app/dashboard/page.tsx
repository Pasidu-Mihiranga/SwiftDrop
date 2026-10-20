import { redirect } from "next/navigation";
import { auth } from "@/auth";
import { homeFor } from "@/lib/roles";

/** Post-login landing: forwards each user to the area for their most privileged role. */
export default async function DashboardPage() {
  const session = await auth();
  if (!session || session.error) redirect("/login");
  redirect(homeFor(session.user.roles));
}
