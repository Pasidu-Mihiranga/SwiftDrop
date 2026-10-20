import time
from types import SimpleNamespace

import jwt
import pytest
from cryptography.hazmat.primitives.asymmetric import rsa

ISSUER = "http://localhost:8180/realms/swiftdrop"


class StaticKeyResolver:
    """Stands in for PyJWKClient: always returns the test public key."""

    def __init__(self, public_key):
        self.public_key = public_key

    def get_signing_key_from_jwt(self, _token):
        return SimpleNamespace(key=self.public_key)


@pytest.fixture(scope="session")
def keypair():
    private_key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
    return private_key, private_key.public_key()


@pytest.fixture
def sign(keypair):
    private_key, _ = keypair

    def _sign(claims: dict, *, audience="swiftdrop-api", issuer=ISSUER, expires_in=300) -> str:
        payload = {"iss": issuer, "aud": audience, "exp": int(time.time()) + expires_in, **claims}
        return jwt.encode(payload, private_key, algorithm="RS256")

    return _sign


@pytest.fixture
def resolver(keypair):
    return StaticKeyResolver(keypair[1])
