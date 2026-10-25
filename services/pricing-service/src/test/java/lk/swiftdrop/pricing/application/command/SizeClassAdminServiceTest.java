package lk.swiftdrop.pricing.application.command;

import lk.swiftdrop.common.web.error.ResourceNotFoundException;
import lk.swiftdrop.pricing.application.port.out.SizeClassRepository;
import lk.swiftdrop.pricing.domain.model.SizeClassCode;
import lk.swiftdrop.pricing.domain.model.SizeClassRate;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SizeClassAdminServiceTest {

    private static final SizeClassRate CURRENT = new SizeClassRate(SizeClassCode.M,
            new BigDecimal("5.00"), "40x30x20", new BigDecimal("1.250"));

    private final SizeClassRepository repository = mock(SizeClassRepository.class);
    private final SizeClassAdminService service = new SizeClassAdminService(repository);

    @Test
    void listsConfiguredClassesWithoutAddingDefaults() {
        when(repository.findAll()).thenReturn(List.of(CURRENT));

        assertThat(service.list()).containsExactly(CURRENT);
    }

    @Test
    void preservesUnspecifiedLimitsWhenChangingTheMultiplier() {
        when(repository.findByCode(SizeClassCode.M)).thenReturn(Optional.of(CURRENT));
        SizeClassRate expected = new SizeClassRate(SizeClassCode.M,
                CURRENT.maxWeightKg(), CURRENT.maxDimensionsCm(), new BigDecimal("1.400"));
        when(repository.save(expected)).thenReturn(expected);

        assertThat(service.update(SizeClassCode.M, null, null, new BigDecimal("1.400"))).isEqualTo(expected);
        verify(repository).save(expected);
    }

    @Test
    void updatesTheConfiguredWeightAndDimensionsTogether() {
        when(repository.findByCode(SizeClassCode.M)).thenReturn(Optional.of(CURRENT));
        SizeClassRate expected = new SizeClassRate(SizeClassCode.M,
                new BigDecimal("7.50"), "45x35x25", new BigDecimal("1.300"));
        when(repository.save(expected)).thenReturn(expected);

        assertThat(service.update(SizeClassCode.M, expected.maxWeightKg(),
                expected.maxDimensionsCm(), expected.multiplier())).isEqualTo(expected);
    }

    @Test
    void rejectsAnUnknownClassRatherThanCreatingAnotherCode() {
        when(repository.findByCode(SizeClassCode.M)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(SizeClassCode.M, null, null, BigDecimal.ONE))
                .isInstanceOfSatisfying(ResourceNotFoundException.class,
                        error -> assertThat(error.code()).isEqualTo("SIZE_CLASS_NOT_FOUND"));
        verify(repository, never()).save(org.mockito.ArgumentMatchers.any());
    }
}
