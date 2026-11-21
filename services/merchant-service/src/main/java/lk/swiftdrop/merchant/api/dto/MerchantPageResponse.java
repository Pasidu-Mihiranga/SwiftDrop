package lk.swiftdrop.merchant.api.dto;

import lk.swiftdrop.merchant.application.model.PagedResult;
import lk.swiftdrop.merchant.domain.model.Merchant;

import java.util.List;

/** The MerchantPage schema: a page of merchants with the shared PageMeta envelope. */
public record MerchantPageResponse(List<MerchantResponse> content, PageMeta page) {

    public record PageMeta(int page, int size, long totalElements, int totalPages) { }

    public static MerchantPageResponse from(PagedResult<Merchant> result) {
        return new MerchantPageResponse(result.content().stream().map(MerchantResponse::from).toList(),
                new PageMeta(result.page(), result.size(), result.totalElements(), result.totalPages()));
    }
}
