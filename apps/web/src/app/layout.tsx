import type { Metadata, Viewport } from "next";
import type { ReactNode } from "react";
import { auth } from "@/auth";
import { ChatAssistant } from "@/components/chat-assistant";
import { SiteHeader } from "@/components/site-header";
import "./globals.css";

export const metadata: Metadata = {
  title: { default: "SwiftDrop — island-wide parcel delivery", template: "%s · SwiftDrop" },
  description: "Send, track and manage parcels anywhere in Sri Lanka.",
};

export const viewport: Viewport = { width: "device-width", initialScale: 1, themeColor: "#0b5ed7" };

export default async function RootLayout({ children }: { children: ReactNode }) {
  const session = await auth();
  const signedIn = Boolean(session && !session.error);

  return (
    <html lang="en" suppressHydrationWarning>
      <body className="min-h-screen" suppressHydrationWarning>
        <SiteHeader />
        <main className="mx-auto w-full max-w-6xl px-4 py-8 pb-24 sm:px-6">{children}</main>
        <ChatAssistant signedIn={signedIn} />
      </body>
    </html>
  );
}
