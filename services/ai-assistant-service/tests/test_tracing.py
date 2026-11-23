import httpx
from fastapi import FastAPI

from app.core.tracing import setup_tracing


def test_tracing_stays_off_without_an_endpoint():
    assert setup_tracing(FastAPI(), httpx.AsyncClient(), endpoint="") is False


def test_tracing_instruments_the_app_and_the_gateway_client():
    app = FastAPI()
    assert setup_tracing(app, httpx.AsyncClient(), endpoint="http://127.0.0.1:1/") is True
    # a second app reuses the same provider
    assert setup_tracing(FastAPI(), httpx.AsyncClient(), endpoint="http://127.0.0.1:1") is True
