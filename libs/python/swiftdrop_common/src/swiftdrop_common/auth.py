"""Keycloak access-token verification (signature, issuer, audience, expiry) for Python services."""

from dataclasses import dataclass, field
from typing import Any

import jwt
from jwt import PyJWKClient

KNOWN_ROLES = frozenset({"CUSTOMER", "MERCHANT", "MERCHANT_API", "COURIER", "DISPATCHER", "ADMIN"})


class InvalidTokenError(Exception):
    """The token is missing, malformed, expired or not issued for SwiftDrop."""


@dataclass(frozen=True)
class AuthenticatedUser:
    id: str
    roles: frozenset[str] = field(default_factory=frozenset)
    email: str | None = None
    name: str | None = None
    merchant_id: str | None = None
    token: str = field(default="", repr=False)

    def has_role(self, role: str) -> bool:
        return role in self.roles

    @classmethod
    def from_claims(cls, claims: dict[str, Any], token: str = "") -> "AuthenticatedUser":
        realm_roles = (claims.get("realm_access") or {}).get("roles") or []
        return cls(
            id=str(claims["sub"]),
            roles=frozenset(r for r in realm_roles if r in KNOWN_ROLES),
            email=claims.get("email"),
            name=claims.get("name"),
            merchant_id=claims.get("merchant_id"),
            token=token,
        )


class JwtVerifier:
    """Verifies RS256 tokens against Keycloak's JWKS (keys are cached by PyJWKClient)."""

    def __init__(self, issuer: str, audience: str = "swiftdrop-api", jwks_uri: str | None = None, key_resolver: Any = None) -> None:
        self.issuer = issuer
        self.audience = audience
        self._resolver = key_resolver or PyJWKClient(jwks_uri or f"{issuer}/protocol/openid-connect/certs", cache_keys=True)

    def verify(self, token: str) -> AuthenticatedUser:
        try:
            signing_key = self._resolver.get_signing_key_from_jwt(token)
            claims = jwt.decode(
                token,
                signing_key.key,
                algorithms=["RS256"],
                audience=self.audience,
                issuer=self.issuer,
                options={"require": ["exp", "sub"]},
            )
        except Exception as exc:  # any decoding/verification/network problem means "not authenticated"
            raise InvalidTokenError("Invalid or expired access token") from exc
        return AuthenticatedUser.from_claims(claims, token)
