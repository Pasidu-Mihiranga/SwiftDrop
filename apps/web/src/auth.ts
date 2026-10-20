import NextAuth from "next-auth";
import Keycloak from "next-auth/providers/keycloak";
import { type Role, rolesFromAccessToken } from "@/lib/roles";
import { keycloakConfig } from "@/lib/keycloak";

const kc = keycloakConfig();

/**
 * Auth.js (OIDC Authorization Code + PKCE) against Keycloak. Tokens live only inside the encrypted,
 * HTTP-only session cookie; the browser-visible session contains the user's id, name, email and roles.
 * Google login is Keycloak identity brokering (kc_idp_hint=google), so every user gets a Keycloak JWT.
 */
export const { handlers, auth, signIn, signOut } = NextAuth({
  trustHost: true,
  session: { strategy: "jwt", maxAge: 8 * 60 * 60 },
  pages: { signIn: "/login" },
  providers: [
    Keycloak({
      clientId: kc.clientId,
      clientSecret: kc.clientSecret,
      issuer: kc.issuer,
      // In Docker the Next.js server reaches Keycloak at a different (internal) URL than the browser.
      ...(kc.internalUrl !== kc.issuer && {
        authorization: { url: `${kc.issuer}/protocol/openid-connect/auth`, params: { scope: "openid profile email" } },
        token: `${kc.internalUrl}/protocol/openid-connect/token`,
        userinfo: `${kc.internalUrl}/protocol/openid-connect/userinfo`,
      }),
    }),
  ],
  callbacks: {
    async jwt({ token, account }) {
      if (account?.access_token) {
        // First sign-in: keep Keycloak's tokens server-side only.
        return {
          ...token,
          accessToken: account.access_token,
          refreshToken: account.refresh_token,
          idToken: account.id_token,
          expiresAt: account.expires_at ?? Math.floor(Date.now() / 1000) + 300,
          roles: rolesFromAccessToken(account.access_token),
          error: undefined,
        };
      }
      // Refresh a minute before expiry.
      if (token.expiresAt && Date.now() / 1000 < token.expiresAt - 60) return token;
      return refreshAccessToken(token);
    },
    async session({ session, token }) {
      session.user.id = token.sub ?? "";
      session.user.roles = (token.roles ?? []) as Role[];
      session.error = token.error;
      return session;
    },
  },
});

async function refreshAccessToken(token: import("next-auth/jwt").JWT): Promise<import("next-auth/jwt").JWT> {
  if (!token.refreshToken) return { ...token, error: "RefreshTokenMissing" };
  try {
    const response = await fetch(`${kc.internalUrl}/protocol/openid-connect/token`, {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
      body: new URLSearchParams({
        grant_type: "refresh_token",
        refresh_token: token.refreshToken,
        client_id: kc.clientId,
        client_secret: kc.clientSecret,
      }),
    });
    const refreshed = (await response.json()) as { access_token?: string; refresh_token?: string; expires_in?: number };
    if (!response.ok || !refreshed.access_token) throw new Error("refresh rejected");
    return {
      ...token,
      accessToken: refreshed.access_token,
      refreshToken: refreshed.refresh_token ?? token.refreshToken,
      expiresAt: Math.floor(Date.now() / 1000) + (refreshed.expires_in ?? 300),
      roles: rolesFromAccessToken(refreshed.access_token),
      error: undefined,
    };
  } catch {
    // Session expired in Keycloak (logout elsewhere, idle timeout): the UI sends the user to sign in again.
    return { ...token, error: "RefreshAccessTokenError" };
  }
}
