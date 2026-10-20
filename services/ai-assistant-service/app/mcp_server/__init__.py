"""MCP server (Phase 4, M10). Planned tools, each a thin wrapper over SwiftDropApi:

- track_parcel(tracking_number)
- estimate_delivery_fee(from_city, to_city, weight_kg, size_class="S")
- list_my_parcels(status=None)            (requires the user's token)
- get_parcel_details(parcel_id)           (requires the user's token)
- list_service_zones()

Served over streamable HTTP at /mcp so external MCP clients can use them too.
"""
