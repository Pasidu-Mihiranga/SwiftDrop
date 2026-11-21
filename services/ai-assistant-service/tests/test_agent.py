import json

import httpx
import pytest
import respx
from fakeredis import FakeAsyncRedis, FakeServer

from app.agent.guardrails import RateLimited, RateLimiter, ScopeGate
from app.agent.orchestrator import AgentOrchestrator
from app.agent.prompts import OFF_TOPIC_ANSWER, ROUND_LIMIT_ANSWER
from app.clients.swiftdrop_api import SwiftDropApi
from app.memory.conversation_store import ConversationStore
from app.privacy import PiiRedactor, VaultCipher
from app.tools import build_registry
from swiftdrop_common import AuthenticatedUser
from tests.support import UNAVAILABLE, ScriptedProvider, call, text

GATEWAY = "http://gateway"
CUSTOMER = AuthenticatedUser(id="user-1", roles=frozenset({"CUSTOMER"}), token="customer-token")
PARCEL = {
    "id": "p-1", "trackingNumber": "SD261020K7Q2M9X", "status": "OUT_FOR_DELIVERY", "pickup": {"city": "Colombo"},
    "dropoff": {"line1": "12 Temple Road", "city": "Kandy", "district": "Kandy", "postalCode": "20000"},
    "recipient": {"name": "Kamal Perera", "phone": "+94771234567", "email": "kamal@example.lk"},
    "fee": {"amount": 650.0, "currency": "LKR"}, "history": [],
}


@pytest.fixture
async def setup():
    redis = FakeAsyncRedis()
    async with httpx.AsyncClient(base_url=GATEWAY) as http:
        def build(provider, per_minute=20, max_rounds=5):
            store = ConversationStore(redis, VaultCipher("test-secret"), 3600, 20)
            return AgentOrchestrator(provider, build_registry(), PiiRedactor(), store, RateLimiter(redis, per_minute),
                                     lambda user: SwiftDropApi(http, user.token if user else None),
                                     max_tool_rounds=max_rounds)
        yield build, redis


async def turn(orchestrator, message, user=CUSTOMER, conversation_id=None, address="10.0.0.1"):
    conversation = await orchestrator.begin(user, conversation_id, address)
    events = [e async for e in orchestrator.run(conversation, message, user)]
    answer = "".join(e.data["text"] for e in events if e.name == "token")
    return events, answer, events[-1].data["conversationId"]


@respx.mock
async def test_the_model_never_sees_personal_data_but_the_user_does(setup):
    build, redis = setup
    respx.get(f"{GATEWAY}/api/v1/parcels/p-1").respond(json=PARCEL)
    provider = ScriptedProvider(
        [call("get_parcel_details", {"parcel_id": "p-1"})],
        text("[NAME_1] at [ADDRESS_1] will get it today; the courier calls [PH", "ONE_1]."),
        # second message: the model refers to the placeholders it learned and to the user's own number
        [call("track_parcel", {"tracking_number": "SD261020K7Q2M9X"}, "call_2")],
        text("I will tell [NAME_1] to call you on [PHONE_2]."),
    )
    orchestrator = build(provider)
    respx.get(f"{GATEWAY}/api/v1/tracking/public/SD261020K7Q2M9X").respond(json={"trackingNumber": "SD261020K7Q2M9X"})

    _, first, conversation_id = await turn(orchestrator, "When does parcel p-1 arrive? Mail me at me@example.lk")
    _, second, same_id = await turn(orchestrator, "Ask Kamal Perera to call me on 0711111111", conversation_id=conversation_id)

    sent = provider.everything_sent()
    for secret in ("Kamal", "Perera", "+94771234567", "kamal@example.lk", "12 Temple Road", "me@example.lk",
                   "0711111111"):
        assert secret not in sent, f"{secret} reached the model"
    assert "[EMAIL_1]" in sent and "[NAME_1]" in sent and "[PHONE_2]" in sent
    assert first == "Kamal Perera at 12 Temple Road will get it today; the courier calls +94771234567."
    assert second == "I will tell Kamal Perera to call you on 0711111111."
    assert same_id == conversation_id

    # memory holds placeholders only, and the vault is encrypted
    stored = b" ".join(await redis.lrange(f"assistant:conv:{conversation_id}:messages", 0, -1))
    assert b"Kamal" not in stored and b"[NAME_1]" in stored
    assert b"Kamal" not in await redis.get(f"assistant:conv:{conversation_id}:vault")


@respx.mock
async def test_tool_chips_and_the_answer_are_streamed_in_order(setup):
    build, _ = setup
    respx.get(f"{GATEWAY}/api/v1/tracking/public/SD261020K7Q2M9X").respond(
        json={"trackingNumber": "SD261020K7Q2M9X", "currentStatus": "IN_TRANSIT", "events": []})
    provider = ScriptedProvider(
        text("Let me check.") + [call("track_parcel", {"tracking_number": "SD261020K7Q2M9X"})],
        text("It is in transit."),
    )
    events, answer, _ = await turn(build(provider), "Where is SD261020K7Q2M9X?", user=None)

    assert [e.name for e in events] == ["token", "tool", "token", "done"]
    assert events[1].data == {"name": "track_parcel", "label": "Checked tracking SD261020K7Q2M9X", "ok": True}
    assert answer == "Let me check.\n\nIt is in transit."
    tool_message = provider.requests[1][0][-1]
    assert tool_message.role == "tool" and json.loads(tool_message.content)["currentStatus"] == "IN_TRANSIT"


async def test_guests_only_get_public_tools_and_cannot_force_private_ones(setup):
    build, _ = setup
    provider = ScriptedProvider([call("list_my_parcels", {})], text("Please sign in first."))
    events, answer, _ = await turn(build(provider), "Show my parcels", user=None)

    assert [t.name for t in provider.requests[0][1]] == ["track_parcel", "estimate_delivery_fee", "list_service_zones"]
    assert events[0].data == {"name": "list_my_parcels", "label": "Could not use list my parcels", "ok": False}
    assert json.loads(provider.requests[1][0][-1].content)["error"] == "SIGN_IN_REQUIRED"
    assert answer == "Please sign in first."


async def test_off_topic_requests_get_the_fallback_even_when_the_sentinel_is_split(setup):
    build, _ = setup
    events, answer, _ = await turn(build(ScriptedProvider(text(" [[OFF", "_TOPIC]]"))), "Write me a poem")
    assert answer == OFF_TOPIC_ANSWER and [e.name for e in events] == ["token", "done"]


async def test_short_answers_that_only_look_like_the_sentinel_are_kept(setup):
    build, _ = setup
    _, answer, _ = await turn(build(ScriptedProvider(text("[["))), "hi")
    assert answer == "[["


async def test_the_tool_round_cap_forces_an_answer(setup):
    build, _ = setup
    looping = [call("list_service_zones", {}, f"call_{i}") for i in range(1)]
    provider = ScriptedProvider(looping, looping, looping)
    events, answer, _ = await turn(build(provider, max_rounds=2), "zones?")

    assert answer == ROUND_LIMIT_ANSWER
    assert provider.requests[-1][1] == []          # the last call offers no tools
    chips = [e.data for e in events if e.name == "tool"]
    assert chips[-1] == {"name": "list_service_zones", "label": "Tool limit reached", "ok": False}


async def test_invalid_tool_arguments_are_reported_to_the_model(setup):
    build, _ = setup
    provider = ScriptedProvider([call("track_parcel", "{not json")], text("Which parcel?"))
    events, _, _ = await turn(build(provider), "track it")
    assert events[0].data["ok"] is False
    assert json.loads(provider.requests[1][0][-1].content)["error"] == "INVALID_ARGUMENTS"


async def test_an_unavailable_model_ends_the_turn_with_an_error_and_saves_nothing(setup):
    build, redis = setup
    events, answer, conversation_id = await turn(build(ScriptedProvider(UNAVAILABLE)), "hello")
    assert [e.name for e in events] == ["error", "done"] and answer == ""
    assert events[0].data["code"] == "ASSISTANT_UNAVAILABLE"
    assert await redis.exists(f"assistant:conv:{conversation_id}:messages") == 0


async def test_rate_limit_per_caller(setup):
    build, _ = setup
    orchestrator = build(ScriptedProvider(), per_minute=2)
    await orchestrator.begin(None, None, "10.0.0.9")
    await orchestrator.begin(None, None, "10.0.0.9")
    with pytest.raises(RateLimited):
        await orchestrator.begin(None, None, "10.0.0.9")
    await orchestrator.begin(None, None, "10.0.0.10")          # another guest has their own budget
    await orchestrator.begin(CUSTOMER, None, "10.0.0.9")       # a signed-in user is counted by id


async def test_a_conversation_cannot_be_continued_by_someone_else(setup):
    build, _ = setup
    orchestrator = build(ScriptedProvider(text("Noted."), text("Hello.")))
    _, _, conversation_id = await turn(orchestrator, "My number is 0771234567")

    other = AuthenticatedUser(id="user-2", roles=frozenset({"CUSTOMER"}), token="t2")
    hijack = await orchestrator.begin(other, conversation_id, "10.0.0.2")
    assert hijack.id != conversation_id and hijack.history == [] and len(hijack.vault) == 0
    assert (await orchestrator.begin(None, conversation_id, "10.0.0.2")).id != conversation_id
    assert (await orchestrator.begin(CUSTOMER, "not-an-id", "10.0.0.2")).new
    own = await orchestrator.begin(CUSTOMER, conversation_id, "10.0.0.1")
    assert own.id == conversation_id and [m.role for m in own.history] == ["user", "assistant"]
    assert own.history[0].content == "My number is [PHONE_1]"


async def test_a_redis_outage_degrades_without_failing_the_turn():
    server = FakeServer()
    server.connected = False
    redis = FakeAsyncRedis(server=server)
    async with httpx.AsyncClient(base_url=GATEWAY) as http:
        orchestrator = AgentOrchestrator(
            ScriptedProvider(text("Hi there.")), build_registry(), PiiRedactor(),
            ConversationStore(redis, VaultCipher("s"), 3600, 20), RateLimiter(redis, 1),
            lambda user: SwiftDropApi(http))
        _, answer, _ = await turn(orchestrator, "hello", user=None)
    assert answer == "Hi there."


def test_scope_gate_holds_only_what_could_still_be_the_sentinel():
    gate = ScopeGate()
    assert gate.feed("Your") == "Your"             # cannot be the sentinel: released at once
    assert gate.feed(" parcel") == " parcel"
    assert gate.finish() == ""
    held = ScopeGate()
    assert held.feed(" [[OFF") == ""                 # could still be the sentinel: held back
    assert held.feed("ICE hours?") == " [[OFFICE hours?"
    assert not held.off_topic
