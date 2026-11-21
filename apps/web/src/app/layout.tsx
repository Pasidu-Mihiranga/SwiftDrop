import type { Metadata, Viewport } from "next";
import type { ReactNode } from "react";
import { Host_Grotesk } from "next/font/google";
import { auth } from "@/auth";
import { ChatAssistant } from "@/components/chat-assistant";
import { MainFrame } from "@/components/main-frame";
import { SiteHeader } from "@/components/site-header";
import "./globals.css";

// One family for display and reading; the display role uses its heaviest weight (800).
const hostGrotesk = Host_Grotesk({ subsets: ["latin"], variable: "--font-host-grotesk", display: "swap" });

export const metadata: Metadata = {
  title: { default: "SwiftDrop — island-wide parcel delivery", template: "%s · SwiftDrop" },
  description: "Send, track and manage parcels anywhere in Sri Lanka.",
};

export const viewport: Viewport = { width: "device-width", initialScale: 1, themeColor: "#be185d" };

export default async function RootLayout({ children }: { children: ReactNode }) {
  const session = await auth();
  const signedIn = Boolean(session && !session.error);

  return (
    <html lang="en" className={hostGrotesk.variable} suppressHydrationWarning>
      <body className="min-h-screen" suppressHydrationWarning>
        <SiteHeader />
        <MainFrame>{children}</MainFrame>
        <ChatAssistant signedIn={signedIn} />
      </body>
    </html>
  );
}
