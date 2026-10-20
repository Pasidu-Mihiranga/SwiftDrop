import pytest
from fastapi import Depends, FastAPI
from fastapi.testclient import TestClient
from pydantic import BaseModel

from swiftdrop_common import ApiError, AuthenticatedUser, InvalidTokenError, JwtVerifier, install_swiftdrop
from swiftdrop_common.web import current_user, optional_user

from .conftest import ISSUER


def test_verifies_and_maps_keycloak_claims(sign, resolver):
    verifier = JwtVerifier(ISSUER, key_resolver=resolver)
    token = sign({"sub": "user-1", "email": "m@swiftdrop.lk", "merchant_id": "m-1", "realm_access": {"roles": ["MERCHANT", "offline_access"]}})

    user = verifier.verify(token)

    assert user.id == "user-1"
    assert user.roles == frozenset({"MERCHANT"})
    assert user.merchant_id == "m-1"
    assert user.has_role("MERCHANT") and not user.has_role("ADMIN")
    assert user.token == token


@pytest.mark.parametrize("override", [{"audience": "other"}, {"issuer": "http://evil"}, {"expires_in": -60}])
def test_rejects_bad_tokens(sign, resolver, override):
    verifier = JwtVerifier(ISSUER, key_resolver=resolver)
    with pytest.raises(InvalidTokenError):
        verifier.verify(sign({"sub": "u"}, **override))


class Body(BaseModel):
    weight_kg: float


@pytest.fixture
def client(resolver):
    app = FastAPI()
    install_swiftdrop(app, JwtVerifier(ISSUER, key_resolver=resolver))

    @app.get("/me")
    async def me(user: AuthenticatedUser = Depends(current_user)):
        return {"id": user.id, "roles": sorted(user.roles)}

    @app.get("/maybe")
    async def maybe(user: AuthenticatedUser | None = Depends(optional_user)):
        return {"anonymous": user is None}

    @app.get("/missing")
    async def missing():
        raise ApiError(404, "PARCEL_NOT_FOUND", "Parcel SD1 was not found")

    @app.post("/quote")
    async def quote(body: Body):
        return body

    @app.get("/boom")
    async def boom():
        raise RuntimeError("password=hunter2")

    return TestClient(app, raise_server_exceptions=False)


def test_requires_token_with_problem_response(client):
    res = client.get("/me")
    assert res.status_code == 401
    assert res.headers["content-type"].startswith("application/problem+json")
    assert res.headers["www-authenticate"] == "Bearer"
    assert res.json()["code"] == "UNAUTHORIZED"
    assert res.json()["traceId"] == res.headers["x-correlation-id"]


def test_accepts_valid_token(client, sign):
    res = client.get("/me", headers={"Authorization": f"Bearer {sign({'sub': 'u-9', 'realm_access': {'roles': ['CUSTOMER']}})}"})
    assert res.status_code == 200
    assert res.json() == {"id": "u-9", "roles": ["CUSTOMER"]}


def test_optional_user_allows_anonymous_but_rejects_bad_tokens(client):
    assert client.get("/maybe").json() == {"anonymous": True}
    assert client.get("/maybe", headers={"Authorization": "Bearer junk"}).status_code == 401


def test_errors_are_problem_details(client):
    assert client.get("/missing").json()["code"] == "PARCEL_NOT_FOUND"
    assert client.get("/nope").json()["code"] == "NOT_FOUND"
    invalid = client.post("/quote", json={"weight_kg": "heavy"}).json()
    assert invalid["code"] == "VALIDATION_FAILED"
    assert invalid["errors"][0]["field"] == "weight_kg"
    boom = client.get("/boom")
    assert boom.status_code == 500
    assert "hunter2" not in boom.text


def test_correlation_metrics_and_health(client):
    res = client.get("/health/live", headers={"X-Correlation-Id": "corr-12345678"})
    assert res.json() == {"status": "UP"}
    assert res.headers["x-correlation-id"] == "corr-12345678"
    assert "http_server_requests_seconds" in client.get("/metrics").text
