package lk.swiftdrop.pricing.api.v1.admin;

import lk.swiftdrop.pricing.domain.model.Dimensions;
import lk.swiftdrop.common.security.KeycloakRealmRoleConverter;
import lk.swiftdrop.common.security.SwiftDropSecurityAutoConfiguration;
import lk.swiftdrop.common.web.SwiftDropWebAutoConfiguration;
import lk.swiftdrop.common.web.error.ResourceNotFoundException;
import lk.swiftdrop.pricing.application.command.SizeClassAdminService;
import lk.swiftdrop.pricing.domain.model.SizeClassCode;
import lk.swiftdrop.pricing.domain.model.SizeClassRate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminSizeClassController.class)
@ImportAutoConfiguration({SwiftDropWebAutoConfiguration.class, SwiftDropSecurityAutoConfiguration.class})
class AdminSizeClassControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private SizeClassAdminService sizeClasses;

    private static RequestPostProcessor callerWithRoles(String... roles) {
        return jwt().jwt(token -> token.subject("admin-1").claim("realm_access", Map.of("roles", List.of(roles))))
                .authorities(new KeycloakRealmRoleConverter());
    }

    @Test
    void anonymousAndNonAdminCallersCannotListConfiguredPrices() throws Exception {
        mvc.perform(get("/api/v1/admin/pricing/size-classes"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/admin/pricing/size-classes").with(callerWithRoles("CUSTOMER")))
                .andExpect(status().isForbidden());
        verify(sizeClasses, never()).list();
    }

    @Test
    void adminCanListAndUpdateExistingClass() throws Exception {
        SizeClassRate current = new SizeClassRate(SizeClassCode.M, new BigDecimal("5.00"),
                Dimensions.of("40", "30", "20"), new BigDecimal("1.250"));
        when(sizeClasses.list()).thenReturn(List.of(current));
        when(sizeClasses.update(SizeClassCode.M, null, null, new BigDecimal("1.250")))
                .thenReturn(current);

        mvc.perform(get("/api/v1/admin/pricing/size-classes").with(callerWithRoles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("M"))
                .andExpect(jsonPath("$[0].maxWeightKg").value(5.00));
        mvc.perform(put("/api/v1/admin/pricing/size-classes/M").with(callerWithRoles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"multiplier\":1.250}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.multiplier").value(1.250));
        verify(sizeClasses).update(SizeClassCode.M, null, null, new BigDecimal("1.250"));
    }

    @Test
    void nonAdminCallerCannotUpdateAClass() throws Exception {
        mvc.perform(put("/api/v1/admin/pricing/size-classes/M").with(callerWithRoles("CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"multiplier\":1.250}"))
                .andExpect(status().isForbidden());
        verify(sizeClasses, never()).update(any(), any(), any(), any());
    }

    @Test
    void invalidMultiplierAndWeightAreValidationErrors() throws Exception {
        mvc.perform(put("/api/v1/admin/pricing/size-classes/M").with(callerWithRoles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"maxWeightKg\":0,\"multiplier\":0.9}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        verify(sizeClasses, never()).update(any(), any(), any(), any());
    }

    @Test
    void unknownClassReturnsNotFound() throws Exception {
        when(sizeClasses.update(SizeClassCode.M, null, null, BigDecimal.ONE))
                .thenThrow(ResourceNotFoundException.of("Size class", SizeClassCode.M));

        mvc.perform(put("/api/v1/admin/pricing/size-classes/M").with(callerWithRoles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"multiplier\":1}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SIZE_CLASS_NOT_FOUND"));
    }
}
