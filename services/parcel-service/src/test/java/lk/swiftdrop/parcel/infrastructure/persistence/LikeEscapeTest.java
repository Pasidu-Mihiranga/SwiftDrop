package lk.swiftdrop.parcel.infrastructure.persistence;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** A merchant's search text goes into a LIKE pattern, so its wildcards must be made literal. */
class LikeEscapeTest {

    @Test
    void plainTextIsUnchanged() {
        assertThat(ParcelJpaRepository.escapeLike("web-1001")).isEqualTo("web-1001");
    }

    @Test
    void wildcardsBecomeLiteral() {
        assertThat(ParcelJpaRepository.escapeLike("50%_off")).isEqualTo("50\\%\\_off");
    }

    @Test
    void theEscapeCharacterItselfIsEscapedFirst() {
        // Otherwise "\%" typed by a merchant would turn back into a wildcard.
        assertThat(ParcelJpaRepository.escapeLike("a\\%")).isEqualTo("a\\\\\\%");
    }
}
