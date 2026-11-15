package lk.swiftdrop.pricing.api.dto;

import lk.swiftdrop.pricing.domain.model.MerchantAgreement;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** A merchant's negotiated terms as published by the admin API. */
public record MerchantAgreementResponse(UUID id, UUID merchantId, BigDecimal discountPct,
                                        BigDecimal discountFlat, boolean waivesRemoteArea,
                                        boolean waivesInsurance, boolean waivesCodFee,
                                        List<LaneOverrideResponse> laneOverrides,
                                        LocalDate effectiveFrom, LocalDate effectiveTo) {

    public record LaneOverrideResponse(String fromZone, String toZone, BigDecimal discountPct,
                                       BigDecimal flatBaseFee) {
    }

    public static MerchantAgreementResponse of(MerchantAgreement agreement) {
        return new MerchantAgreementResponse(agreement.id(), agreement.merchantId(),
                agreement.discountPct(), agreement.discountFlat(), agreement.waivesRemoteArea(),
                agreement.waivesInsurance(), agreement.waivesCodFee(),
                agreement.laneOverrides().stream()
                        .map(o -> new LaneOverrideResponse(o.fromZone().value(), o.toZone().value(),
                                o.discountPct(), o.flatBaseFee()))
                        .toList(),
                agreement.effectiveFrom(), agreement.effectiveTo());
    }
}
