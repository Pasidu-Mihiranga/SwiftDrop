import json

import httpx
import pytest
from fakeredis import FakeAsyncRedis
from fastapi.testclient import TestClient
from mcp import Client

from app.clients.swiftdrop_api import SwiftDropApi
from app.core.config import Settings
from app.main import create_app
from app.mcp_server.server import build_mcp_server
from app.privacy import PiiRedactor
from app.tools import build_registry
from tests.support import token, verifier

PARCEL = {"id": "p-1", "trackingNumber": "SD261020K7Q2M9X", "status": "OUT_FOR_DELIVERY",
          "recipient": {"name": "Kamal Perera", "phone": "+94771234567"},
          "dropoff": {"line1": "12 Temple Road", "city": "Kandy", "district": "Kandy", "postalCode": "20000"}}


def gateway(request: httpx.Request) -> httpx.Response:
    if request.url.path == "/api/v1/parcels/p-1":
        return httpx.Response(200, json=PARCEL)
    if request.url.path == "/api/v1/tracking/public/SD261020K7Q2M9X":
        return httpx.Response(200, json={"trackingNumber": "SD261020K7Q2M9X", "currentStatus": "IN_TRANSIT",
                                         "recipient": "Kamal Perera", "events": []})
    return httpx.Response(404)


def mcp_call(client: TestClient, name: str, arguments: dict, bearer: str | None = None) -> dict:
    headers = {"Accept": "application/json, text/event-stream"}
    if bearer:
        headers["Authorization"] = f"Bearer {bearer}"
    body = {"jsonrpc": "2.0", "id": 1, "method": "tools/call", "params": {"name": name, "arguments": arguments}}
    response = client.post("/mcp", json=body, headers=headers)
    assert response.status_code == 200
    return response.json()["result"]


def app_client(**settings) -> TestClient:
    app = create_app(Settings(_env_file=None, **settings), verifier(), provider=None, redis=FakeAsyncRedis(),
                     gateway_transport=httpx.MockTransport(gateway))
    return TestClient(app)


async def test_the_same_tools_are_listed_with_their_schemas():
    async with httpx.AsyncClient(base_url="http://gw", transport=httpx.MockTransport(gateway)) as http:
        server = build_mcp_server(build_registry(), verifier(), lambda u: SwiftDropApi(http), PiiRedactor())
        async with Client(server) as client:
            tools = {t.name: t for t in (await client.list_tools()).tools}
            assert list(tools) == ["track_parcel", "estimate_delivery_fee", "list_service_zones", "list_my_parcels",
                                   "get_parcel_details"]
            assert tools["estimate_delivery_fee"].input_schema["required"] == ["from_district", "to_district", "weight_kg"]
            tracked = await client.call_tool("track_parcel", {"tracking_number": "SD261020K7Q2M9X"})
    assert not tracked.is_error
    assert tracked.structured_content["result"]["recipient"] == "K*** P."


def test_private_tools_need_a_valid_token_and_results_are_masked():
    with app_client() as client:
        anonymous = mcp_call(client, "get_parcel_details", {"parcel_id": "p-1"})
        assert anonymous["isError"] and "SIGN_IN_REQUIRED" in anonymous["content"][0]["text"]

        forged = mcp_call(client, "get_parcel_details", {"parcel_id": "p-1"}, bearer="not-a-token")
        assert forged["isError"] and "invalid" in forged["content"][0]["text"]

        signed_in = mcp_call(client, "get_parcel_details", {"parcel_id": "p-1"}, bearer=token(["CUSTOMER"]))
        assert not signed_in["isError"]
        parcel = signed_in["structuredContent"]["result"]
        assert parcel["recipient"] == {"name": "K*** P.", "phone": "+94*******67"}
        assert parcel["dropoff"]["line1"] == "***" and parcel["dropoff"]["city"] == "Kandy"
        assert "Kamal" not in json.dumps(signed_in)


@pytest.mark.parametrize("mask, expected", [(True, "K*** P."), (False, "Kamal Perera")])
def test_masking_can_be_turned_off_for_trusted_clients(mask, expected):
    with app_client(mcp_mask_pii=mask) as client:
        result = mcp_call(client, "get_parcel_details", {"parcel_id": "p-1"}, bearer=token(["CUSTOMER"]))
    assert result["structuredContent"]["result"]["recipient"]["name"] == expected


def test_public_tools_work_without_a_token():
    with app_client() as client:
        result = mcp_call(client, "track_parcel", {"tracking_number": "SD261020K7Q2M9X"})
        missing = mcp_call(client, "list_service_zones", {})
    assert result["structuredContent"]["result"]["currentStatus"] == "IN_TRANSIT"
    # the mock gateway has no zones endpoint: an upstream 404 becomes a readable tool error
    assert missing["isError"] and "NOT_FOUND: The requested item was not found." in missing["content"][0]["text"]
