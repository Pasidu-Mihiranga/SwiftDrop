package lk.swiftdrop.pricing.domain.policy;

import lk.swiftdrop.pricing.domain.model.ChargeableWeight;
import lk.swiftdrop.pricing.domain.model.Dimensions;
import lk.swiftdrop.pricing.domain.model.WeightBasis;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChargeableWeightPolicyTest {

    private static final BigDecimal DIVISOR = new BigDecimal("5000");

    @Test
    void aBulkyParcelIsChargedOnTheSpaceItTakesUpNotWhatItWeighs() {
        // A 60x40x30 box is 72000 cubic centimetres, which is 14.4 kg of van.
        ChargeableWeight weight = ChargeableWeightPolicy.of(
                new BigDecimal("3"), Dimensions.of("60", "40", "30"), DIVISOR);

        assertThat(weight.actualKg()).isEqualByComparingTo("3");
        assertThat(weight.volumetricKg()).isEqualByComparingTo("14.40");
        assertThat(weight.chargeableKg()).isEqualByComparingTo("14.40");
        assertThat(weight.basis()).isEqualTo(WeightBasis.VOLUMETRIC);
    }

    @Test
    void aDenseParcelIsStillChargedOnItsWeight() {
        ChargeableWeight weight = ChargeableWeightPolicy.of(
                new BigDecimal("20"), Dimensions.of("30", "20", "15"), DIVISOR);

        assertThat(weight.volumetricKg()).isEqualByComparingTo("1.80");
        assertThat(weight.chargeableKg()).isEqualByComparingTo("20");
        assertThat(weight.basis()).isEqualTo(WeightBasis.ACTUAL);
    }

    @Test
    void anUnmeasuredParcelIsChargedExactlyAsItWasBeforeVolumetricWeightExisted() {
        ChargeableWeight weight = ChargeableWeightPolicy.of(new BigDecimal("3"), null, DIVISOR);

        assertThat(weight.chargeableKg()).isEqualByComparingTo("3");
        assertThat(weight.volumetricKg()).isNull();
        assertThat(weight.basis()).isEqualTo(WeightBasis.ACTUAL);
    }

    @Test
    void withNoDivisorConfiguredTheVolumetricStepIsSkippedRatherThanFailing() {
        Dimensions bulky = Dimensions.of("60", "40", "30");

        assertThat(ChargeableWeightPolicy.of(new BigDecimal("3"), bulky, null).chargeableKg())
                .isEqualByComparingTo("3");
        assertThat(ChargeableWeightPolicy.of(new BigDecimal("3"), bulky, BigDecimal.ZERO).chargeableKg())
                .isEqualByComparingTo("3");
    }

    @Test
    void aTieGoesToActualWeightSoTheBasisIsNeverMisreported() {
        // 100x100x50 over 5000 is exactly 100 kg.
        ChargeableWeight weight = ChargeableWeightPolicy.of(
                new BigDecimal("100"), Dimensions.of("100", "100", "50"), DIVISOR);

        assertThat(weight.chargeableKg()).isEqualByComparingTo("100");
        assertThat(weight.basis()).isEqualTo(WeightBasis.ACTUAL);
    }

    @Test
    void refusesAWeightThatIsNotPositive() {
        assertThatThrownBy(() -> ChargeableWeightPolicy.of(BigDecimal.ZERO, null, DIVISOR))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ChargeableWeightPolicy.of(null, null, DIVISOR))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
