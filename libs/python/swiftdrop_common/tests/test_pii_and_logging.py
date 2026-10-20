import json
import logging

import pytest

from swiftdrop_common import configure_logging, mask_pii
from swiftdrop_common.log import JsonFormatter, correlation_id_var


@pytest.mark.parametrize(
    ("text", "expected"),
    [
        ("Sent receipt to nimali.perera@gmail.com", "Sent receipt to n***@gmail.com"),
        ("Call 0771234567 now", "Call 07******67 now"),
        ("Call +94771234567 now", "Call +94*******67 now"),
        ("Call +94 77 123 4567", "Call +94 ** *** **67"),
        ('{"username":"a","password":"S3cret!"}', '{"username":"a","password":"***"}'),
        ("grant_type=client_credentials&client_secret=abc123&x=1", "grant_type=client_credentials&client_secret=***&x=1"),
        ("Authorization: Bearer eyJhbGciOiJSUzI1NiJ9.payload.sig", "Authorization: Bearer ***"),
    ],
)
def test_masks_like_java_and_node(text, expected):
    assert mask_pii(text) == expected


def test_leaves_dates_ids_and_tracking_numbers_alone():
    text = "Parcel SD261020ABC123 created 2026-10-20T10:15:30Z id=7f9c2ba4-e88f-4c2b-9d3a-1b2c3d4e5f60 at 1729419330000"
    assert mask_pii(text) == text
    assert mask_pii(None) is None


def test_json_formatter_masks_and_adds_correlation_id():
    formatter = JsonFormatter("ai-assistant-service")
    token = correlation_id_var.set("corr-12345678")
    try:
        try:
            raise ValueError("phone 0771234567")
        except ValueError:
            record = logging.getLogger("t").makeRecord("t", logging.ERROR, __file__, 1, "mail %s", ("kamal@example.com",), exc_info=True)
            import sys

            record.exc_info = sys.exc_info()
        line = json.loads(formatter.format(record))
    finally:
        correlation_id_var.reset(token)

    assert line["service"] == "ai-assistant-service"
    assert line["level"] == "ERROR"
    assert line["message"] == "mail k***@example.com"
    assert line["correlationId"] == "corr-12345678"
    assert "0771234567" not in line["exception"]


def test_configure_logging_installs_single_json_handler():
    configure_logging("svc", "DEBUG")
    root = logging.getLogger()
    assert len(root.handlers) == 1
    assert isinstance(root.handlers[0].formatter, JsonFormatter)
    assert logging.getLogger("uvicorn.access").propagate is True
