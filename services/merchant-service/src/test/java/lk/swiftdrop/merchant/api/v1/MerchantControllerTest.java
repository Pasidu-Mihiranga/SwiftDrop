package lk.swiftdrop.merchant.api.v1;

import lk.swiftdrop.common.security.AuthenticatedUser;
import lk.swiftdrop.common.security.KeycloakRealmRoleConverter;
import lk.swiftdrop.common.security.SwiftDropSecurityAutoConfiguration;
import lk.swiftdrop.common.web.SwiftDropWebAutoConfiguration;
import lk.swiftdrop.merchant.MerchantFixtures;
import lk.swiftdrop.merchant.application.command.MerchantCredentialService;
import lk.swiftdrop.merchant.application.command.MerchantOnboardingService;
import lk.swiftdrop.merchant.application.exception.ApiCredentialNotFoundException;
import lk.swiftdrop.merchant.application.exception.IdentityProviderUnavailableException;
import lk.swiftdrop.merchant.application.model.RotatedCredential;
import lk.swiftdrop.merchant.domain.exception.MerchantNotApprovedException;
import lk.swiftdrop.merchant.domain.model.MerchantStatus;
import lk.swiftdrop.merchant.application.query.MerchantQueryService;
import lk.swiftdrop.merchant.domain.exception.BrNumberAlreadyRegisteredException;
import lk.swiftdrop.merchant.domain.exception.MerchantApplicationExistsException;
import lk.swiftdrop.merchant.domain.exception.MerchantNotFoundException;
import lk.swiftdrop.merchant.domain.model.BusinessDetails;
import lk.swiftdrop.merchant.domain.model.Merchant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MerchantController.class)
@ImportAutoConfiguration({SwiftDropWebAutoConfiguration.class, SwiftDropSecurityAutoConfiguration.class})
class MerchantControllerTest {

    private static final String APPLICATION = """
            {"businessName":"Lanka Books","brNumber":"PV 12345","contactEmail":"lankabooks@example.com",
             "contactPhone":"0771234567","website":"https://lankabooks.example.com",
             "pickupAddress":{"line1":"12 Temple Road","city":"Kandy","district":"Kandy","postalCode":"20000"}}
            """;

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;
    @MockitoBean
    private MerchantOnboardingService onboarding;
    @MockitoBean
    private MerchantQueryService merchants;
    @MockitoBean
    private MerchantCredentialService credentials;

    private static RequestPostProcessor as(String userId, String... roles) {
        return jwt().jwt(token -> token.subject(userId).claim("realm_access", Map.of("roles", List.of(roles))))
                .authorities(new KeycloakRealmRoleConverter());
    }

    private org.springframework.test.web.servlet.ResultActions register(RequestPostProcessor caller, String body)
            throws Exception {
        return mvc.perform(post("/api/v1/merchants/register").with(caller)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    void aSignedInUserAppliesAndGetsThePendingApplicationBack() throws Exception {
        when(onboarding.register(any(), any())).thenReturn(MerchantFixtures.stored(MerchantFixtures.pending()));

        register(as("user-1", "CUSTOMER"), APPLICATION)
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith("/api/v1/merchants/me")))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.brNumber").value("PV12345"))
                .andExpect(jsonPath("$.pickupAddress.city").value("Kandy"))
                .andExpect(jsonPath("$.rejectionReason").doesNotExist());

        ArgumentCaptor<AuthenticatedUser> applicant = ArgumentCaptor.forClass(AuthenticatedUser.class);
        ArgumentCaptor<BusinessDetails> business = ArgumentCaptor.forClass(BusinessDetails.class);
        verify(onboarding).register(applicant.capture(), business.capture());
        assertThat(applicant.getValue().id()).isEqualTo("user-1");
        assertThat(business.getValue().brNumber()).isEqualTo("PV12345");
    }

    @Test
    void aUserWithoutAnyRoleMayAlsoApply() throws Exception {
        when(onboarding.register(any(), any())).thenReturn(MerchantFixtures.stored(MerchantFixtures.pending()));

        register(as("user-1"), APPLICATION).andExpect(status().isCreated());
    }

    @Test
    void anOwnerIdInTheBodyIsIgnored() throws Exception {
        when(onboarding.register(any(), any())).thenReturn(MerchantFixtures.stored(MerchantFixtures.pending()));
        String withOwner = APPLICATION.replaceFirst("\\{", "{\"ownerUserId\":\"someone-else\",");

        register(as("user-1", "CUSTOMER"), withOwner).andExpect(status().isCreated());

        verify(onboarding).register(org.mockito.ArgumentMatchers.argThat(user -> user.id().equals("user-1")), any());
    }

    @Test
    void anAnonymousCallerIsRefused() throws Exception {
        mvc.perform(post("/api/v1/merchants/register").contentType(MediaType.APPLICATION_JSON).content(APPLICATION))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/merchants/me")).andExpect(status().isUnauthorized());
        verifyNoInteractions(onboarding, merchants);
    }

    @Test
    void aMerchantMachineClientCannotApplyOrReadAnApplication() throws Exception {
        register(as("service-account-merchant-demo", "MERCHANT_API"), APPLICATION).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/merchants/me").with(as("service-account-merchant-demo", "MERCHANT_API")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(onboarding, merchants);
    }

    @Test
    void everyInvalidFieldIsReportedAndNothingIsStored() throws Exception {
        String invalid = """
                {"businessName":" ","brNumber":"PV#1","contactEmail":"not-an-email","contactPhone":"12345",
                 "website":"ftp://shop","pickupAddress":{"line1":"","city":"Kandy","district":"Kandy","postalCode":"2"}}
                """;

        register(as("user-1", "CUSTOMER"), invalid)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", hasItem("businessName")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("brNumber")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("contactEmail")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("contactPhone")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("website")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("pickupAddress.line1")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("pickupAddress.postalCode")));
        verifyNoInteractions(onboarding);
    }

    @Test
    void anEmailWithoutADottedDomainIsRefused() throws Exception {
        register(as("user-1", "CUSTOMER"), APPLICATION.replace("lankabooks@example.com", "owner@localhost"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", hasItem("contactEmail")));
    }

    @Test
    void aMissingPickupAddressIsRefused() throws Exception {
        String noAddress = """
                {"businessName":"Lanka Books","brNumber":"PV1","contactEmail":"a@example.com","contactPhone":"0771234567"}
                """;

        register(as("user-1", "CUSTOMER"), noAddress)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", hasItem("pickupAddress")));
    }

    @Test
    void aRegistrationNumberInUseIsAConflictWithoutRepeatingTheNumber() throws Exception {
        when(onboarding.register(any(), any())).thenThrow(new BrNumberAlreadyRegisteredException());

        register(as("user-1", "CUSTOMER"), APPLICATION)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MERCHANT_BR_NUMBER_TAKEN"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("12345"))));
    }

    @Test
    void asecondApplicationWhileOneIsInPlayIsAConflict() throws Exception {
        when(onboarding.register(any(), any())).thenThrow(new MerchantApplicationExistsException());

        register(as("user-1", "CUSTOMER"), APPLICATION)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MERCHANT_APPLICATION_EXISTS"));
    }

    @Test
    void theApplicantReadsTheirOwnApplicationIncludingARejectionReason() throws Exception {
        Merchant rejected = MerchantFixtures.stored(
                MerchantFixtures.pending().reject("admin-1", "BR certificate is unreadable", MerchantFixtures.NOW));
        when(merchants.mine("user-1")).thenReturn(rejected);

        mvc.perform(get("/api/v1/merchants/me").with(as("user-1", "CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.rejectionReason").value("BR certificate is unreadable"))
                .andExpect(jsonPath("$.reviewedBy").doesNotExist());
        verify(merchants).mine(eq("user-1"));
    }

    @Test
    void aUserWhoNeverAppliedGets404() throws Exception {
        when(merchants.mine("user-2")).thenThrow(new MerchantNotFoundException());

        mvc.perform(get("/api/v1/merchants/me").with(as("user-2", "CUSTOMER")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MERCHANT_NOT_FOUND"));
    }

    // --- credential rotation

    private static final String MERCHANT_ID = "7e726c28-4a72-4fea-9ec1-c56b08262ddc";
    private static final String ROTATE = "/api/v1/merchants/me/credentials/rotate";

    private static RequestPostProcessor merchantUser(String userId, String merchantId, String... roles) {
        return jwt().jwt(token -> token.subject(userId).claim("merchant_id", merchantId)
                        .claim("realm_access", Map.of("roles", List.of(roles))))
                .authorities(new KeycloakRealmRoleConverter());
    }

    @Test
    void theMerchantOwnerGetsTheNewSecretOnceAndItIsNotCached() throws Exception {
        when(credentials.rotate(any())).thenReturn(new RotatedCredential("merchant-" + MERCHANT_ID, "s3cret-new"));

        mvc.perform(post(ROTATE).with(merchantUser("user-1", MERCHANT_ID, "MERCHANT")))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Pragma", "no-cache"))
                .andExpect(jsonPath("$.clientId").value("merchant-" + MERCHANT_ID))
                .andExpect(jsonPath("$.clientSecret").value("s3cret-new"))
                .andExpect(jsonPath("$.tokenUrl", endsWith("/realms/swiftdrop/protocol/openid-connect/token")));

        ArgumentCaptor<AuthenticatedUser> caller = ArgumentCaptor.forClass(AuthenticatedUser.class);
        verify(credentials).rotate(caller.capture());
        assertThat(caller.getValue().id()).isEqualTo("user-1");
        assertThat(caller.getValue().merchantId()).contains(MERCHANT_ID);
    }

    @Test
    void aMerchantIdInTheRequestIsIgnored() throws Exception {
        when(credentials.rotate(any())).thenReturn(new RotatedCredential("merchant-" + MERCHANT_ID, "s3cret-new"));

        mvc.perform(post(ROTATE + "?merchantId=someone-else").with(merchantUser("user-1", MERCHANT_ID, "MERCHANT"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"merchantId\":\"someone-else\"}"))
                .andExpect(status().isOk());

        verify(credentials).rotate(org.mockito.ArgumentMatchers.argThat(
                user -> user.merchantId().orElseThrow().equals(MERCHANT_ID)));
    }

    @Test
    void onlyAMerchantPersonMayRotate() throws Exception {
        mvc.perform(post(ROTATE)).andExpect(status().isUnauthorized());
        mvc.perform(post(ROTATE).with(as("user-1", "CUSTOMER"))).andExpect(status().isForbidden());
        mvc.perform(post(ROTATE).with(as("admin-1", "ADMIN"))).andExpect(status().isForbidden());
        // a merchant's machine client cannot replace its own secret, even with a merchant_id
        mvc.perform(post(ROTATE).with(merchantUser("service-account-merchant-x", MERCHANT_ID, "MERCHANT_API")))
                .andExpect(status().isForbidden());
        mvc.perform(post(ROTATE).with(merchantUser("user-1", MERCHANT_ID, "MERCHANT", "MERCHANT_API")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(credentials);
    }

    @Test
    void rotationRefusalsUseTheirOwnCodes() throws Exception {
        when(credentials.rotate(any()))
                .thenThrow(new MerchantNotApprovedException(MerchantStatus.SUSPENDED))
                .thenThrow(new ApiCredentialNotFoundException())
                .thenThrow(new IdentityProviderUnavailableException("The identity provider is not reachable right now"));

        mvc.perform(post(ROTATE).with(merchantUser("user-1", MERCHANT_ID, "MERCHANT")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("MERCHANT_NOT_APPROVED"));
        mvc.perform(post(ROTATE).with(merchantUser("user-1", MERCHANT_ID, "MERCHANT")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("API_CREDENTIAL_NOT_FOUND"));
        mvc.perform(post(ROTATE).with(merchantUser("user-1", MERCHANT_ID, "MERCHANT")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("IDENTITY_PROVIDER_UNAVAILABLE"))
                .andExpect(jsonPath("$.clientSecret").doesNotExist());
    }
}
