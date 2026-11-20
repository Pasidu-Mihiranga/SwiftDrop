package lk.swiftdrop.merchant.application.port.out;

import lk.swiftdrop.merchant.domain.model.ApiCredential;

import java.util.Optional;
import java.util.UUID;

/** Stores the record of each merchant's Keycloak client. A merchant has at most one that is not revoked. */
public interface ApiCredentialRepository {

    ApiCredential save(ApiCredential credential);

    Optional<ApiCredential> findActiveByMerchant(UUID merchantId);
}
