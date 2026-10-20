import type { Metadata, Viewport } from "next";
import type { ReactNode } from "react";
import { SiteHeader } from "@/components/site-header";
import "./globals.css";

export const metadata: Metadata = {
  title: { default: "SwiftDrop — island-wide parcel delivery", template: "%s · SwiftDrop" },
  description: "Send, track and manage parcels anywhere in Sri Lanka.",
};

export const viewport: Viewport = { width: "device-width", initialScale: 1, themeColor: "#0b5ed7" };

export default function RootLayout({ children }: { children: ReactNode }) {
  return (
    <html lang="en">
      <body className="min-h-screen">
        <SiteHeader />
        <main className="mx-auto w-full max-w-6xl px-4 py-8 sm:px-6">{children}</main>
      </body>
    </html>
  );
}
