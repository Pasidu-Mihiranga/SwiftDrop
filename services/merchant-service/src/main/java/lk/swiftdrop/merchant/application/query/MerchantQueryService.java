package lk.swiftdrop.merchant.application.query;

import lk.swiftdrop.merchant.application.model.PagedResult;
import lk.swiftdrop.merchant.application.port.out.MerchantRepository;
import lk.swiftdrop.merchant.domain.exception.MerchantNotFoundException;
import lk.swiftdrop.merchant.domain.model.Merchant;
import lk.swiftdrop.merchant.domain.model.MerchantStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read side of onboarding: the applicant's own status and the admin approval queue. */
@Service
@Transactional(readOnly = true)
public class MerchantQueryService {

    private final MerchantRepository merchants;

    public MerchantQueryService(MerchantRepository merchants) {
        this.merchants = merchants;
    }

    /**
     * The caller's most recent application, so a rejected applicant still sees the reason.
     *
     * @throws MerchantNotFoundException when the caller has never applied
     */
    public Merchant mine(String ownerUserId) {
        return merchants.findLatestByOwner(ownerUserId).orElseThrow(MerchantNotFoundException::new);
    }

    /** One page of applications in one status, oldest first. */
    public PagedResult<Merchant> byStatus(MerchantStatus status, int page, int size) {
        return merchants.findByStatus(status, page, size);
    }
}
