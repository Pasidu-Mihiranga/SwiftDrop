package lk.swiftdrop.merchant.api.v1.admin;

import lk.swiftdrop.common.security.KeycloakRealmRoleConverter;
import lk.swiftdrop.common.security.SwiftDropSecurityAutoConfiguration;
import lk.swiftdrop.common.web.SwiftDropWebAutoConfiguration;
import lk.swiftdrop.merchant.MerchantFixtures;
import lk.swiftdrop.merchant.application.command.MerchantOnboardingService;
import lk.swiftdrop.merchant.application.model.PagedResult;
import lk.swiftdrop.merchant.application.query.MerchantQueryService;
import lk.swiftdrop.merchant.domain.exception.InvalidMerchantTransitionException;
import lk.swiftdrop.merchant.domain.exception.MerchantNotFoundException;
import lk.swiftdrop.merchant.domain.model.Merchant;
import lk.swiftdrop.merchant.domain.model.MerchantStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static lk.swiftdrop.merchant.MerchantFixtures.NOW;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminMerchantController.class)
@ImportAutoConfiguration({SwiftDropWebAutoConfiguration.class, SwiftDropSecurityAutoConfiguration.class})
class AdminMerchantControllerTest {

    private static final UUID ID = UUID.randomUUID();
    private static final String BASE = "/api/v1/admin/merchants";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;
    @MockitoBean
    private MerchantOnboardingService onboarding;
    @MockitoBean
    private MerchantQueryService merchants;

    private static RequestPostProcessor as(String userId, String role) {
        return jwt().jwt(token -> token.subject(userId).claim("realm_access", Map.of("roles", List.of(role))))
                .authorities(new KeycloakRealmRoleConverter());
    }

    private static RequestPostProcessor admin() {
        return as("admin-1", "ADMIN");
    }

    @Test
    void theQueueDefaultsToTheFirstPageOfPendingApplications() throws Exception {
        when(merchants.byStatus(MerchantStatus.PENDING, 0, 20)).thenReturn(
                new PagedResult<>(List.of(MerchantFixtures.stored(MerchantFixtures.pending())), 0, 20, 1, 1));

        mvc.perform(get(BASE).with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].status").value("PENDING"))
                .andExpect(jsonPath("$.content[0].businessName").value("Lanka Books"))
                .andExpect(jsonPath("$.page.page").value(0))
                .andExpect(jsonPath("$.page.size").value(20))
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.page.totalPages").value(1));
    }

    @Test
    void theQueueCanBeFilteredByAnotherStatusAndPaged() throws Exception {
        when(merchants.byStatus(MerchantStatus.APPROVED, 2, 5)).thenReturn(new PagedResult<>(List.of(), 2, 5, 10, 2));

        mvc.perform(get(BASE + "?status=APPROVED&page=2&size=5").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.page.totalElements").value(10));
        verify(merchants).byStatus(MerchantStatus.APPROVED, 2, 5);
    }

    @Test
    void anUnknownStatusIsABadRequest() throws Exception {
        mvc.perform(get(BASE + "?status=DELETED").with(admin())).andExpect(status().isBadRequest());
        verifyNoInteractions(merchants);
    }

    @ParameterizedTest(name = "{0} is refused")
    @ValueSource(strings = {"page=-1", "size=0", "size=101"})
    void pagingOutsideTheLimitsIsABadRequest(String query) throws Exception {
        mvc.perform(get(BASE + "?" + query).with(admin())).andExpect(status().isBadRequest());
        verifyNoInteractions(merchants);
    }

    @Test
    void approvingReturnsTheApprovedMerchant() throws Exception {
        Merchant approved = MerchantFixtures.stored(MerchantFixtures.pending().approve("admin-1", NOW));
        when(onboarding.approve(any(), eq(ID))).thenReturn(approved);

        mvc.perform(post(BASE + "/" + ID + "/approve").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.reviewedAt").exists());
        verify(onboarding).approve(argThat(user -> user.id().equals("admin-1")), eq(ID));
    }

    @Test
    void rejectingPassesTheReasonOn() throws Exception {
        Merchant rejected = MerchantFixtures.stored(MerchantFixtures.pending().reject("admin-1", "Incomplete", NOW));
        when(onboarding.reject(any(), eq(ID), eq("Incomplete"))).thenReturn(rejected);

        mvc.perform(post(BASE + "/" + ID + "/reject").with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Incomplete\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.rejectionReason").value("Incomplete"));
    }

    @Test
    void theReasonIsTrimmedBeforeItsLengthIsChecked() throws Exception {
        String reason = "x".repeat(500);
        Merchant rejected = MerchantFixtures.stored(MerchantFixtures.pending().reject("admin-1", reason, NOW));
        when(onboarding.reject(any(), eq(ID), eq(reason))).thenReturn(rejected);

        mvc.perform(post(BASE + "/" + ID + "/reject").with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"  " + reason + "  \"}"))
                .andExpect(status().isOk());
        verify(onboarding).reject(any(), eq(ID), eq(reason));
    }

    @Test
    void aRejectionWithoutABodyIsABadRequest() throws Exception {
        mvc.perform(post(BASE + "/" + ID + "/reject").with(admin()).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(onboarding);
    }

    @Test
    void aRejectionWithoutAReasonIsRefusedBeforeAnythingChanges() throws Exception {
        mvc.perform(post(BASE + "/" + ID + "/reject").with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", hasItem("reason")));
        mvc.perform(post(BASE + "/" + ID + "/reject").with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"" + "x".repeat(501) + "\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(onboarding);
    }

    @Test
    void suspendingReturnsTheSuspendedMerchant() throws Exception {
        Merchant suspended = MerchantFixtures.stored(
                MerchantFixtures.pending().approve("admin-1", NOW).suspend("admin-1", NOW));
        when(onboarding.suspend(any(), eq(ID))).thenReturn(suspended);

        mvc.perform(post(BASE + "/" + ID + "/suspend").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUSPENDED"));
    }

    @Test
    void aDecisionTheLifecycleDoesNotAllowIs422() throws Exception {
        when(onboarding.suspend(any(), eq(ID)))
                .thenThrow(new InvalidMerchantTransitionException(MerchantStatus.PENDING, MerchantStatus.SUSPENDED));

        mvc.perform(post(BASE + "/" + ID + "/suspend").with(admin()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("MERCHANT_INVALID_TRANSITION"));
    }

    @Test
    void anUnknownMerchantIs404() throws Exception {
        when(onboarding.approve(any(), eq(ID))).thenThrow(new MerchantNotFoundException());

        mvc.perform(post(BASE + "/" + ID + "/approve").with(admin()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MERCHANT_NOT_FOUND"));
    }

    @Test
    void aMalformedIdIsABadRequest() throws Exception {
        mvc.perform(post(BASE + "/not-a-uuid/approve").with(admin())).andExpect(status().isBadRequest());
        verifyNoInteractions(onboarding);
    }

    @ParameterizedTest(name = "a {0} cannot use the approval queue")
    @ValueSource(strings = {"CUSTOMER", "MERCHANT", "MERCHANT_API", "COURIER", "DISPATCHER"})
    void onlyAnAdminMayReviewMerchants(String role) throws Exception {
        RequestPostProcessor caller = as("user-1", role);

        mvc.perform(get(BASE).with(caller)).andExpect(status().isForbidden());
        mvc.perform(post(BASE + "/" + ID + "/approve").with(caller)).andExpect(status().isForbidden());
        mvc.perform(post(BASE + "/" + ID + "/reject").with(caller)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"No\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post(BASE + "/" + ID + "/suspend").with(caller)).andExpect(status().isForbidden());
        verifyNoInteractions(onboarding, merchants);
    }

    @Test
    void anAnonymousCallerIsRefused() throws Exception {
        mvc.perform(get(BASE)).andExpect(status().isUnauthorized());
        mvc.perform(post(BASE + "/" + ID + "/approve")).andExpect(status().isUnauthorized());
        verifyNoInteractions(onboarding, merchants);
    }
}
