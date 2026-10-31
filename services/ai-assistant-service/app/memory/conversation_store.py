"""Conversation memory in Redis: pseudonymized messages and the encrypted vault, both expiring together."""

import json
import logging
import re
import uuid
from dataclasses import dataclass, field
from datetime import UTC, datetime

from redis.asyncio import Redis
from redis.exceptions import RedisError

from app.llm.base import ChatMessage
from app.privacy import PseudonymVault, VaultCipher

log = logging.getLogger(__name__)
_CONVERSATION_ID = re.compile(r"^[0-9a-f]{32}$")


@dataclass
class Conversation:
    id: str
    owner: str
    history: list[ChatMessage] = field(default_factory=list)
    vault: PseudonymVault = field(default_factory=PseudonymVault)
    new: bool = True


class ConversationStore:
    def __init__(self, redis: Redis, cipher: VaultCipher, ttl_seconds: int, max_messages: int) -> None:
        self._redis = redis
        self._cipher = cipher
        self._ttl = ttl_seconds
        self._max = max_messages

    async def load(self, conversation_id: str | None, owner: str) -> Conversation:
        """The caller's conversation, or a new one. An id that belongs to someone else, has expired or is
        malformed simply starts a new conversation, so nobody can read or extend another user's history."""
        if conversation_id and _CONVERSATION_ID.match(conversation_id):
            try:
                stored_owner = await self._redis.hget(self._key(conversation_id, "meta"), "owner")
                if stored_owner is not None and stored_owner.decode() == owner:
                    raw = await self._redis.lrange(self._key(conversation_id, "messages"), 0, -1)
                    blob = await self._redis.get(self._key(conversation_id, "vault"))
                    history = [ChatMessage(**json.loads(m)) for m in raw]
                    return Conversation(conversation_id, owner, history, self._cipher.decrypt(blob), new=False)
            except RedisError:
                log.warning("conversation memory unavailable, starting a new conversation")
        return Conversation(uuid.uuid4().hex, owner)

    async def save(self, conversation: Conversation, messages: list[ChatMessage]) -> None:
        """Appends pseudonymized messages and stores the vault encrypted; everything expires together."""
        meta, history, vault = (self._key(conversation.id, k) for k in ("meta", "messages", "vault"))
        try:
            async with self._redis.pipeline(transaction=True) as pipe:
                if conversation.new:
                    pipe.hset(meta, mapping={"owner": conversation.owner,
                                             "started_at": datetime.now(UTC).isoformat()})
                for message in messages:
                    pipe.rpush(history, json.dumps({"role": message.role, "content": message.content}))
                pipe.ltrim(history, -self._max, -1)
                pipe.set(vault, self._cipher.encrypt(conversation.vault))
                for key in (meta, history, vault):
                    pipe.expire(key, self._ttl)
                await pipe.execute()
        except RedisError:
            log.warning("could not save conversation %s", conversation.id)

    @staticmethod
    def _key(conversation_id: str, part: str) -> str:
        return f"assistant:conv:{conversation_id}:{part}"
