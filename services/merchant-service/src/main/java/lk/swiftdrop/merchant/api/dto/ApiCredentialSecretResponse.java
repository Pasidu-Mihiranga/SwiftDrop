package lk.swiftdrop.merchant.api.dto;

import lk.swiftdrop.merchant.application.model.RotatedCredential;

import java.net.URI;

/**
 * The ApiCredentialSecret schema in contracts/openapi/merchant-service.v1.yaml: everything a
 * merchant's system needs to request tokens. Sent once, with {@code Cache-Control: no-store}.
 * {@link #toString()} masks the secret.
 *
 * @param tokenUrl the OAuth 2.0 token endpoint to call with grant_type=client_credentials
 */
public record ApiCredentialSecretResponse(String clientId, String clientSecret, URI tokenUrl) {

    public static ApiCredentialSecretResponse from(RotatedCredential rotated, URI tokenUrl) {
        return new ApiCredentialSecretResponse(rotated.clientId(), rotated.clientSecret(), tokenUrl);
    }

    @Override
    public String toString() {
        return "ApiCredentialSecretResponse[clientId=" + clientId + ", clientSecret=****, tokenUrl=" + tokenUrl + "]";
    }
}
