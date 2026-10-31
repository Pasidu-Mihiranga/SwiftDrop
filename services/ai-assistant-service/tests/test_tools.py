import httpx
import pytest
import respx

from app.clients.swiftdrop_api import SwiftDropApi
from app.tools import build_registry
from swiftdrop_common import ApiError, AuthenticatedUser

GATEWAY = "http://gateway"
CUSTOMER = AuthenticatedUser(id="user-1", roles=frozenset({"CUSTOMER"}), token="customer-token")


@pytest.fixture
async def http():
    async with httpx.AsyncClient(base_url=GATEWAY) as client:
        yield client


@pytest.fixture
def registry():
    return build_registry()


def test_guests_are_offered_public_tools_only(registry):
    assert [t.name for t in registry.tools_for(None)] == ["track_parcel", "estimate_delivery_fee", "list_service_zones"]
    assert len(registry.specs_for(CUSTOMER)) == 5
    spec = next(s for s in registry.specs_for(None) if s.name == "estimate_delivery_fee")
    assert spec.parameters["required"] == ["from_district", "to_district", "weight_kg"]
    assert "title" not in spec.parameters


@respx.mock
async def test_guest_cannot_call_a_private_tool_even_by_name(registry, http):
    route = respx.get(f"{GATEWAY}/api/v1/parcels/mine")
    result = await registry.call("list_my_parcels", {}, None, SwiftDropApi(http))
    assert not result.ok and result.error_code == "SIGN_IN_REQUIRED"
    assert not route.called


@respx.mock
async def test_tools_forward_the_users_token_and_minimise_the_result(registry, http):
    route = respx.get(f"{GATEWAY}/api/v1/parcels/mine", params={"size": "10", "status": "IN_TRANSIT"}).respond(json={
        "content": [{"id": "p-1", "trackingNumber": "SD261020K7Q2M9X", "status": "IN_TRANSIT",
                     "dropoff": {"line1": "12 Temple Road", "city": "Kandy"},
                     "recipient": {"name": "Kamal Perera"}, "createdAt": "2026-10-20T08:15:29Z"}],
        "page": {"number": 0}})
    result = await registry.call("list_my_parcels", {"status": "IN_TRANSIT"}, CUSTOMER,
                                 SwiftDropApi(http, CUSTOMER.token))
    assert result.ok and result.chip == "Listed your parcels"
    assert result.data == [{"id": "p-1", "trackingNumber": "SD261020K7Q2M9X", "status": "IN_TRANSIT",
                            "to": "Kandy", "createdAt": "2026-10-20T08:15:29Z"}]
    assert route.calls.last.request.headers["authorization"] == "Bearer customer-token"


@respx.mock
async def test_track_parcel_normalises_the_number_and_keeps_recent_events(registry, http):
    events = [{"status": s, "city": "Kandy", "at": f"2026-10-2{i}T10:00:00Z"}
              for i, s in enumerate(["CREATED", "PICKED_UP", "IN_TRANSIT", "IN_TRANSIT", "OUT_FOR_DELIVERY", "DELIVERED"])]
    route = respx.get(f"{GATEWAY}/api/v1/tracking/public/SD261020K7Q2M9X").respond(json={
        "trackingNumber": "SD261020K7Q2M9X", "currentStatus": "DELIVERED", "from": "Colombo", "to": "Kandy",
        "recipient": "K*** P.", "events": events})
    result = await registry.call("track_parcel", {"tracking_number": "sd 2610 20k7q2m9x"}, None, SwiftDropApi(http))
    assert result.ok and result.chip == "Checked tracking SD261020K7Q2M9X"
    assert len(result.data["recentEvents"]) == 5 and result.data["recentEvents"][-1]["status"] == "DELIVERED"
    assert "authorization" not in route.calls.last.request.headers


@respx.mock
async def test_fee_estimate_posts_a_quote_request(registry, http):
    route = respx.post(f"{GATEWAY}/api/v1/pricing/quotes").respond(json={
        "quoteId": "q-1", "fee": {"amount": 650.0, "currency": "LKR"}, "breakdown": [], "fromZone": "WP",
        "toZone": "CP", "expiresAt": "2026-10-31T10:15:00Z"})
    result = await registry.call("estimate_delivery_fee",
                                 {"from_district": " colombo", "to_district": "kandy", "weight_kg": 2}, None,
                                 SwiftDropApi(http))
    assert result.ok and result.data["fee"]["amount"] == 650.0 and "quoteId" not in result.data
    sent = route.calls.last.request
    assert sent.read() == (b'{"fromDistrict":"Colombo","toDistrict":"Kandy","weightKg":2.0,'
                           b'"sizeClass":"S","serviceLevel":"STANDARD"}')


@respx.mock
async def test_zones_and_parcel_details(registry, http):
    respx.get(f"{GATEWAY}/api/v1/pricing/zones").respond(json=[
        {"code": "CP", "name": "Central", "districts": ["Kandy"], "active": True},
        {"code": "XX", "name": "Closed", "districts": [], "active": False}])
    respx.get(f"{GATEWAY}/api/v1/parcels/p-1").respond(json={
        "id": "p-1", "trackingNumber": "SD261020K7Q2M9X", "status": "OUT_FOR_DELIVERY",
        "pickup": {"city": "Colombo"}, "dropoff": {"line1": "12 Temple Road", "city": "Kandy", "district": "Kandy",
                                                   "postalCode": "20000", "lat": 7.29},
        "recipient": {"name": "Kamal Perera", "phone": "+94771234567", "email": "kamal@example.lk"},
        "fee": {"amount": 650.0, "currency": "LKR"},
        "history": [{"fromStatus": "CREATED", "toStatus": "PICKED_UP", "occurredAt": "2026-10-30T09:00:00Z"}]})
    zones = await registry.call("list_service_zones", {}, None, SwiftDropApi(http))
    assert zones.data == [{"code": "CP", "name": "Central", "districts": ["Kandy"]}]
    details = await registry.call("get_parcel_details", {"parcel_id": "p-1"}, CUSTOMER, SwiftDropApi(http, "t"))
    assert details.data["recipient"] == {"name": "Kamal Perera", "phone": "+94771234567"}
    assert "lat" not in details.data["dropoff"]
    assert details.data["recentHistory"] == [{"status": "PICKED_UP", "at": "2026-10-30T09:00:00Z"}]


async def test_invalid_and_unknown_calls_become_tool_errors(registry, http):
    api = SwiftDropApi(http)
    bad = await registry.call("track_parcel", {"tracking_number": "not a number"}, None, api)
    assert bad.error_code == "INVALID_ARGUMENTS" and "tracking_number" in bad.data
    extra = await registry.call("list_service_zones", {"surprise": 1}, None, api)
    assert extra.error_code == "INVALID_ARGUMENTS"
    unknown = await registry.call("drop_tables", {}, CUSTOMER, api)
    assert unknown.error_code == "UNKNOWN_TOOL" and unknown.chip == "Could not use an unknown tool"
    assert unknown.for_model() == {"error": "UNKNOWN_TOOL", "message": "There is no tool called drop_tables."}


@respx.mock
async def test_upstream_errors_are_mapped(http):
    respx.get(f"{GATEWAY}/api/v1/tracking/public/SDMISSING00").respond(404)
    respx.get(f"{GATEWAY}/api/v1/pricing/zones").respond(503)
    respx.get(f"{GATEWAY}/api/v1/parcels/p-2").mock(side_effect=httpx.ConnectError("refused"))
    api = SwiftDropApi(http, "t")
    with pytest.raises(ApiError) as missing:
        await api.track("SDMISSING00")
    assert (missing.value.status, missing.value.code) == (404, "NOT_FOUND")
    with pytest.raises(ApiError) as down:
        await api.zones()
    assert down.value.code == "UPSTREAM_ERROR"
    with pytest.raises(ApiError) as unreachable:
        await api.parcel("p-2")
    assert unreachable.value.status == 502

    registry = build_registry()
    result = await registry.call("track_parcel", {"tracking_number": "SDMISSING00"}, None, api)
    assert (result.ok, result.error_code, result.data) == (False, "NOT_FOUND", "The requested item was not found.")
