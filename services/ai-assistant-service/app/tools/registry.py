"""One registry of tools for both the chat agent and the MCP server, with sign-in based access."""

import logging
from collections.abc import Awaitable, Callable
from dataclasses import dataclass
from typing import Any

from pydantic import BaseModel, ValidationError

from app.clients.swiftdrop_api import SwiftDropApi
from app.llm.base import ToolSpec
from swiftdrop_common import ApiError, AuthenticatedUser

log = logging.getLogger(__name__)


class ToolError(Exception):
    """A tool could not run. The message goes to the model, so it never contains personal data."""

    def __init__(self, code: str, message: str) -> None:
        super().__init__(message)
        self.code = code


@dataclass(frozen=True)
class Tool:
    name: str
    description: str
    args: type[BaseModel]
    handler: Callable[[Any, SwiftDropApi], Awaitable[Any]]
    # A short note for the chat widget ("Checked tracking SD..."); it must not contain personal data.
    chip: Callable[[Any], str]
    public: bool = False

    def spec(self) -> ToolSpec:
        schema = self.args.model_json_schema()
        schema.pop("title", None)
        return ToolSpec(self.name, self.description, schema)


@dataclass(frozen=True)
class ToolResult:
    name: str
    ok: bool
    chip: str
    data: Any = None
    error_code: str | None = None

    def for_model(self) -> Any:
        return self.data if self.ok else {"error": self.error_code, "message": self.data}


class ToolRegistry:
    def __init__(self, tools: list[Tool] | None = None) -> None:
        self._tools: dict[str, Tool] = {}
        for tool in tools or []:
            self.register(tool)

    def register(self, tool: Tool) -> None:
        if tool.name in self._tools:
            raise ValueError(f"duplicate tool {tool.name}")
        self._tools[tool.name] = tool

    def get(self, name: str) -> Tool | None:
        return self._tools.get(name)

    def tools_for(self, user: AuthenticatedUser | None) -> list[Tool]:
        """Guests get the public tools only; signed-in users get all of them."""
        return [t for t in self._tools.values() if t.public or user is not None]

    def specs_for(self, user: AuthenticatedUser | None) -> list[ToolSpec]:
        return [t.spec() for t in self.tools_for(user)]

    async def call(self, name: str, raw_args: dict[str, Any], user: AuthenticatedUser | None,
                   api: SwiftDropApi) -> ToolResult:
        """Validates, authorizes and runs a tool. Failures become results the model can explain."""
        tool = self._tools.get(name)
        try:
            if tool is None:
                raise ToolError("UNKNOWN_TOOL", f"There is no tool called {name}.")
            # Checked here, not only when offering tools: a model can name a tool it was not given.
            if not tool.public and user is None:
                raise ToolError("SIGN_IN_REQUIRED", "The user must sign in to use this.")
            try:
                args = tool.args.model_validate(raw_args or {})
            except ValidationError as exc:
                fields = ", ".join(".".join(str(p) for p in e["loc"]) or "arguments" for e in exc.errors())
                raise ToolError("INVALID_ARGUMENTS", f"Invalid or missing: {fields}.") from exc
            try:
                data = await tool.handler(args, api)
            except ApiError as exc:
                raise ToolError(exc.code, str(exc)) from exc
            return ToolResult(name, True, tool.chip(args), data)
        except ToolError as exc:
            log.info("tool %s failed: %s", name, exc.code)
            chip = "Could not use " + (tool.name.replace("_", " ") if tool else "an unknown tool")
            return ToolResult(name, False, chip, str(exc), exc.code)
