import type { DefaultSession } from "next-auth";
import type { Role } from "@/lib/roles";

declare module "next-auth" {
  interface Session {
    user: DefaultSession["user"] & { id: string; roles: Role[] };
    error?: string;
  }
}

declare module "next-auth/jwt" {
  /** Server-side only (encrypted cookie) — never sent to the browser. */
  interface JWT {
    accessToken?: string;
    refreshToken?: string;
    idToken?: string;
    expiresAt?: number;
    roles?: Role[];
    error?: string;
  }
}
