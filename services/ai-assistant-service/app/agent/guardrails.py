"""Guardrails the code enforces: a rate limit per caller and a scope check on the answer."""

import logging
import time
from collections.abc import Callable

from redis.asyncio import Redis
from redis.exceptions import RedisError

from app.agent.prompts import OFF_TOPIC

log = logging.getLogger(__name__)


class RateLimited(Exception):
    pass


class RateLimiter:
    """Fixed one-minute windows in Redis, per signed-in user or, for guests, per client address."""

    def __init__(self, redis: Redis, per_minute: int, clock: Callable[[], float] = time.time) -> None:
        self._redis = redis
        self._per_minute = per_minute
        self._clock = clock

    async def allow(self, subject: str) -> bool:
        key = f"assistant:rate:{subject}:{int(self._clock() // 60)}"
        try:
            count = await self._redis.incr(key)
            if count == 1:
                await self._redis.expire(key, 120)
        except RedisError:
            # Failing open: a Redis outage should degrade protection, not take the assistant down.
            log.warning("rate limiter unavailable, allowing the request")
            return True
        return count <= self._per_minute


class ScopeGate:
    """Holds back only the first characters of an answer, enough to recognise the off-topic sentinel the
    model is told to send. Everything after that streams straight through."""

    def __init__(self) -> None:
        self._buffer = ""
        self.decided = False
        self.off_topic = False

    def feed(self, text: str) -> str:
        if self.decided:
            return "" if self.off_topic else text
        self._buffer += text
        head = self._buffer.lstrip()
        if head.startswith(OFF_TOPIC):
            self.decided = self.off_topic = True
            return ""
        if len(head) >= len(OFF_TOPIC) or not OFF_TOPIC.startswith(head):
            self.decided = True
            released, self._buffer = self._buffer, ""
            return released
        return ""

    def finish(self) -> str:
        """End of the reply: release whatever is still held (a short answer, or a partial sentinel)."""
        if self.decided:
            return ""
        self.decided = True
        released, self._buffer = self._buffer, ""
        return released
