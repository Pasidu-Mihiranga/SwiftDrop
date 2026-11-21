package lk.swiftdrop.dispatch.domain;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class AllocationRulesTest {
    @Test void projectsAllLifecycleStates() {
        for (String status : java.util.List.of("CREATED", "PICKED_UP", "IN_TRANSIT", "OUT_FOR_DELIVERY"))
            assertTrue(AllocationRules.active(status));
        for (String status : java.util.List.of("DELIVERED", "FAILED", "CANCELLED")) {
            assertFalse(AllocationRules.active(status));
            assertThrows(IllegalArgumentException.class, () -> AllocationRules.validate(status,null,"c1"));
        }
    }
    @Test void validatesReassignment() {
        assertDoesNotThrow(() -> AllocationRules.validate("CREATED",null,"c1"));
        assertDoesNotThrow(() -> AllocationRules.validate("CREATED","c1","c2"));
        assertThrows(IllegalArgumentException.class, () -> AllocationRules.validate("CREATED","c1","c1"));
    }
}
