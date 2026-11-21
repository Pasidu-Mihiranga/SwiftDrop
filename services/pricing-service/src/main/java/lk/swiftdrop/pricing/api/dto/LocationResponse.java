package lk.swiftdrop.pricing.api.dto;

/** Where a map point falls: the district to quote from or to, and the zone that serves it. */
public record LocationResponse(String district, String zoneCode) { }
