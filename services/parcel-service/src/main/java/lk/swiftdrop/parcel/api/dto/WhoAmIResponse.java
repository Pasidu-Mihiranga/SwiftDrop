package lk.swiftdrop.parcel.api.dto;

import java.util.Set;

public record WhoAmIResponse(String service, String userId, Set<String> roles, String merchantId) {
}
