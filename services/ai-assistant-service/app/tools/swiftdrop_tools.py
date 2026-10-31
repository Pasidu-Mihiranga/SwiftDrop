"""The five SwiftDrop tools. Each returns only the fields an answer needs (data minimization)."""

from typing import Any, Literal

from pydantic import BaseModel, ConfigDict, Field, field_validator

from app.clients.swiftdrop_api import SwiftDropApi
from app.tools.registry import Tool, ToolRegistry

ParcelStatus = Literal["CREATED", "PICKED_UP", "IN_TRANSIT", "OUT_FOR_DELIVERY", "DELIVERED", "FAILED", "CANCELLED"]
_RECENT_EVENTS = 5


class _Args(BaseModel):
    model_config = ConfigDict(extra="forbid")


class TrackParcelArgs(_Args):
    tracking_number: str = Field(description="SwiftDrop tracking number, e.g. SD261020K7Q2M9X")

    @field_validator("tracking_number")
    @classmethod
    def _normalise(cls, value: str) -> str:
        value = "".join(value.split()).upper()
        if not (value.startswith("SD") and 10 <= len(value) <= 22 and value.isalnum()):
            raise ValueError("not a SwiftDrop tracking number")
        return value


class EstimateFeeArgs(_Args):
    from_district: str = Field(min_length=2, max_length=40, description="Pickup district, e.g. Colombo")
    to_district: str = Field(min_length=2, max_length=40, description="Delivery district, e.g. Kandy")
    weight_kg: float = Field(gt=0, le=100, description="Parcel weight in kilograms")
    size_class: Literal["S", "M", "L", "XL"] = Field("S", description="Parcel size class")
    service_level: Literal["STANDARD", "EXPRESS"] = "STANDARD"


class NoArgs(_Args):
    pass


class ListMyParcelsArgs(_Args):
    status: ParcelStatus | None = Field(None, description="Only parcels in this status")


class ParcelDetailsArgs(_Args):
    parcel_id: str = Field(min_length=1, max_length=64, pattern=r"^[A-Za-z0-9-]+$", description="Parcel id")


async def _track(args: TrackParcelArgs, api: SwiftDropApi) -> dict[str, Any]:
    view = await api.track(args.tracking_number)
    return {
        "trackingNumber": view.get("trackingNumber"),
        "currentStatus": view.get("currentStatus"),
        "from": view.get("from"),
        "to": view.get("to"),
        "recipient": view.get("recipient"),
        "recentEvents": [
            {"status": e.get("status"), "city": e.get("city"), "at": e.get("at")}
            for e in (view.get("events") or [])[-_RECENT_EVENTS:]
        ],
    }


async def _estimate(args: EstimateFeeArgs, api: SwiftDropApi) -> dict[str, Any]:
    quote = await api.quote({
        "fromDistrict": args.from_district.strip().title(),
        "toDistrict": args.to_district.strip().title(),
        "weightKg": args.weight_kg,
        "sizeClass": args.size_class,
        "serviceLevel": args.service_level,
    })
    return {k: quote.get(k) for k in ("fee", "breakdown", "fromZone", "toZone", "expiresAt")}


async def _zones(_args: NoArgs, api: SwiftDropApi) -> list[dict[str, Any]]:
    return [{"code": z.get("code"), "name": z.get("name"), "districts": z.get("districts", [])}
            for z in await api.zones() if z.get("active", True)]


async def _my_parcels(args: ListMyParcelsArgs, api: SwiftDropApi) -> list[dict[str, Any]]:
    page = await api.my_parcels(args.status)
    return [{
        "id": p.get("id"),
        "trackingNumber": p.get("trackingNumber"),
        "status": p.get("status"),
        "to": (p.get("dropoff") or {}).get("city"),
        "createdAt": p.get("createdAt"),
    } for p in (page.get("content") or [])]


async def _parcel(args: ParcelDetailsArgs, api: SwiftDropApi) -> dict[str, Any]:
    parcel = await api.parcel(args.parcel_id)
    dropoff = parcel.get("dropoff") or {}
    recipient = parcel.get("recipient") or {}
    return {
        "id": parcel.get("id"),
        "trackingNumber": parcel.get("trackingNumber"),
        "status": parcel.get("status"),
        "from": (parcel.get("pickup") or {}).get("city"),
        "dropoff": {k: dropoff.get(k) for k in ("line1", "city", "district", "postalCode")},
        "recipient": {"name": recipient.get("name"), "phone": recipient.get("phone")},
        "fee": parcel.get("fee"),
        "recentHistory": [{"status": h.get("toStatus"), "at": h.get("occurredAt")}
                          for h in (parcel.get("history") or [])[-_RECENT_EVENTS:]],
    }


def build_registry() -> ToolRegistry:
    return ToolRegistry([
        Tool("track_parcel",
             "Current status and recent events of a parcel, from its tracking number. Works for anyone.",
             TrackParcelArgs, _track, lambda a: f"Checked tracking {a.tracking_number}", public=True),
        Tool("estimate_delivery_fee",
             "Delivery fee quote between two Sri Lankan districts for a weight and size class. Works for anyone.",
             EstimateFeeArgs, _estimate, lambda a: f"Estimated the fee {a.from_district} to {a.to_district}",
             public=True),
        Tool("list_service_zones",
             "SwiftDrop service zones and the districts in each. Use it to map a city to its district.",
             NoArgs, _zones, lambda _a: "Looked up the service zones", public=True),
        Tool("list_my_parcels",
             "The signed-in user's own parcels, newest first, optionally filtered by status.",
             ListMyParcelsArgs, _my_parcels, lambda _a: "Listed your parcels"),
        Tool("get_parcel_details",
             "Full details of one of the signed-in user's parcels, by parcel id from list_my_parcels.",
             ParcelDetailsArgs, _parcel, lambda _a: "Opened a parcel"),
    ])
