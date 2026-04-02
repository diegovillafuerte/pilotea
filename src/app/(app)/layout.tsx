import { redirect } from "next/navigation";
import { getCurrentDriver } from "@/lib/auth/session";

export default async function AppLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  // Server-side session verification: validates the session token against
  // the database. This is the real auth gate — the Edge middleware only
  // does format checking since it can't access the database.
  const driver = await getCurrentDriver();

  if (!driver) {
    redirect("/login");
  }

  return <div className="min-h-screen">{children}</div>;
}
