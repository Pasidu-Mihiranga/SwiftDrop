"""JSON logging with the field names shared by every SwiftDrop service (shipped to Loki by Alloy)."""

import json
import logging
import sys
from contextvars import ContextVar
from datetime import UTC, datetime

from swiftdrop_common.pii import mask_pii

correlation_id_var: ContextVar[str | None] = ContextVar("correlation_id", default=None)


class JsonFormatter(logging.Formatter):
    def __init__(self, service: str) -> None:
        super().__init__()
        self.service = service

    def format(self, record: logging.LogRecord) -> str:
        entry: dict[str, object] = {
            "@timestamp": datetime.fromtimestamp(record.created, UTC).isoformat().replace("+00:00", "Z"),
            "level": record.levelname,
            "service": self.service,
            "logger": record.name,
            "message": mask_pii(record.getMessage()),
        }
        correlation_id = correlation_id_var.get()
        if correlation_id:
            entry["correlationId"] = correlation_id
        if record.exc_info:
            entry["exception"] = mask_pii(self.formatException(record.exc_info))
        return json.dumps(entry, ensure_ascii=False)


def configure_logging(service: str, level: str = "INFO") -> None:
    """Replaces the root handlers (including uvicorn's) with a single masked JSON handler on stdout."""
    handler = logging.StreamHandler(sys.stdout)
    handler.setFormatter(JsonFormatter(service))
    root = logging.getLogger()
    root.handlers = [handler]
    root.setLevel(level)
    for name in ("uvicorn", "uvicorn.error", "uvicorn.access"):
        logger = logging.getLogger(name)
        logger.handlers = []
        logger.propagate = True
