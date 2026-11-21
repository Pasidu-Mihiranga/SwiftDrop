package lk.swiftdrop.merchant.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lk.swiftdrop.merchant.domain.model.Merchant;

/**
 * Why an application was turned down. The applicant reads it, so it must not hold internal notes.
 *
 * <p>Surrounding whitespace is removed before validation, so the length limit applies to the text
 * that is actually stored.
 */
public record RejectMerchantRequest(@NotBlank @Size(max = Merchant.MAX_REASON_LENGTH) String reason) {

    public RejectMerchantRequest {
        reason = reason == null ? null : reason.strip();
    }
}
