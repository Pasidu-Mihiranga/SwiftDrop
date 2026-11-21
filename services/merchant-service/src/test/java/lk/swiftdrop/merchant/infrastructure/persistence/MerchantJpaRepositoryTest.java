package lk.swiftdrop.merchant.infrastructure.persistence;

import lk.swiftdrop.merchant.domain.exception.BrNumberAlreadyRegisteredException;
import lk.swiftdrop.merchant.domain.exception.MerchantApplicationExistsException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

/** How a unique-index violation from PostgreSQL becomes the 409 the client sees. */
class MerchantJpaRepositoryTest {

    private static DataIntegrityViolationException violation(String postgresMessage) {
        return new DataIntegrityViolationException("could not execute statement", new SQLException(postgresMessage));
    }

    @Test
    void theRegistrationNumberIndexBecomesItsOwnConflict() {
        RuntimeException translated = MerchantJpaRepository.translate(violation(
                "ERROR: duplicate key value violates unique constraint \"uq_merchant_br_number_active\""));

        assertThat(translated).isInstanceOf(BrNumberAlreadyRegisteredException.class);
    }

    @Test
    void theOwnerIndexBecomesTheApplicationExistsConflict() {
        RuntimeException translated = MerchantJpaRepository.translate(violation(
                "ERROR: duplicate key value violates unique constraint \"uq_merchant_owner_active\""));

        assertThat(translated).isInstanceOf(MerchantApplicationExistsException.class);
    }

    @Test
    void anyOtherViolationIsLeftAlone() {
        DataIntegrityViolationException other = violation("ERROR: new row violates check constraint \"ck_merchant_status\"");

        assertThat(MerchantJpaRepository.translate(other)).isSameAs(other);
    }
}
