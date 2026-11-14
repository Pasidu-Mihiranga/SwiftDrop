package lk.swiftdrop.user.api.v1;

import lk.swiftdrop.common.security.KeycloakRealmRoleConverter;
import lk.swiftdrop.common.security.SwiftDropSecurityAutoConfiguration;
import lk.swiftdrop.common.web.SwiftDropWebAutoConfiguration;
import lk.swiftdrop.common.web.error.ConflictException;
import lk.swiftdrop.user.application.command.UserProfileService;
import lk.swiftdrop.user.application.model.IdentityClaims;
import lk.swiftdrop.user.domain.model.PhoneNumber;
import lk.swiftdrop.user.domain.model.ProfileStatus;
import lk.swiftdrop.user.domain.model.UserProfile;
import lk.swiftdrop.user.domain.model.UserRole;
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

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@ImportAutoConfiguration({SwiftDropWebAutoConfiguration.class, SwiftDropSecurityAutoConfiguration.class})
class UserControllerTest {

    private static final Instant CREATED = Instant.parse("2026-11-01T08:00:00Z");

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private UserProfileService profiles;

    /** A person token with the given realm roles, converted exactly as the service does at runtime. */
    private static RequestPostProcessor person(String email, String... roles) {
        return jwt()
                .jwt(token -> {
                    token.subject("sub-1").claim("name", "Nimal Perera")
                            .claim("realm_access", Map.of("roles", List.of(roles)));
                    if (email != null) {
                        token.claim("email", email);
                    }
                })
                .authorities(new KeycloakRealmRoleConverter());
    }

    private static UserProfile profile(PhoneNumber phone) {
        return new UserProfile("sub-1", "nimal@example.lk", "Nimal Perera", phone, ProfileStatus.ACTIVE,
                Set.of(UserRole.CUSTOMER), CREATED, CREATED);
    }

    @Test
    void returnsTheCallersProfileAndPassesTheTokenClaimsThrough() throws Exception {
        when(profiles.getOrCreate(any())).thenReturn(profile(PhoneNumber.parse("0771234567")));

        mvc.perform(get("/api/v1/users/me").with(person("nimal@example.lk", "CUSTOMER", "offline_access")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("sub-1"))
                .andExpect(jsonPath("$.email").value("nimal@example.lk"))
                .andExpect(jsonPath("$.fullName").value("Nimal Perera"))
                .andExpect(jsonPath("$.phone").value("+94771234567"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.roles[0]").value("CUSTOMER"))
                .andExpect(jsonPath("$.createdAt").value("2026-11-01T08:00:00Z"));

        ArgumentCaptor<IdentityClaims> claims = ArgumentCaptor.forClass(IdentityClaims.class);
        verify(profiles).getOrCreate(claims.capture());
        assertThat(claims.getValue()).isEqualTo(
                new IdentityClaims("sub-1", "nimal@example.lk", "Nimal Perera", Set.of(UserRole.CUSTOMER)));
    }

    @Test
    void everyPersonRoleHasAProfile() throws Exception {
        when(profiles.getOrCreate(any())).thenReturn(profile(null));

        for (String role : List.of("CUSTOMER", "MERCHANT", "COURIER", "DISPATCHER", "ADMIN")) {
            mvc.perform(get("/api/v1/users/me").with(person("nimal@example.lk", role)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.phone").doesNotExist());
        }
    }

    @Test
    void rejectsCallersWithoutAToken() throws Exception {
        mvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        verifyNoInteractions(profiles);
    }

    @Test
    void aMerchantMachineClientHasNoProfile() throws Exception {
        mvc.perform(get("/api/v1/users/me").with(person(null, "MERCHANT_API")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        verifyNoInteractions(profiles);
    }

    @Test
    void aPersonTokenWithoutAnEmailIsRefused() throws Exception {
        mvc.perform(get("/api/v1/users/me").with(person(null, "CUSTOMER")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("EMAIL_REQUIRED"));
        verifyNoInteractions(profiles);
    }

    @Test
    void anEmailAlreadyLinkedToAnotherProfileIsAConflict() throws Exception {
        when(profiles.getOrCreate(any())).thenThrow(new ConflictException("EMAIL_ALREADY_LINKED", "linked"));

        mvc.perform(get("/api/v1/users/me").with(person("nimal@example.lk", "CUSTOMER")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_LINKED"));
    }

    @Test
    void updatesTheNameAndNormalisesTheLocalPhoneForm() throws Exception {
        when(profiles.updateContactDetails(any(), any(), any())).thenReturn(profile(PhoneNumber.parse("0771234567")));

        mvc.perform(put("/api/v1/users/me").with(person("nimal@example.lk", "CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Nimal Perera\",\"phone\":\"0771234567\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phone").value("+94771234567"));

        verify(profiles).updateContactDetails(any(), eq("Nimal Perera"), eq(new PhoneNumber("+94771234567")));
    }

    @Test
    void leavingThePhoneOutRemovesIt() throws Exception {
        when(profiles.updateContactDetails(any(), any(), any())).thenReturn(profile(null));

        mvc.perform(put("/api/v1/users/me").with(person("nimal@example.lk", "CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Nimal Perera\"}"))
                .andExpect(status().isOk());

        verify(profiles).updateContactDetails(any(), eq("Nimal Perera"), isNull());
    }

    @Test
    void rejectsAnInvalidPhoneWithoutEchoingIt() throws Exception {
        mvc.perform(put("/api/v1/users/me").with(person("nimal@example.lk", "CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Nimal Perera\",\"phone\":\"0112999888\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(content -> assertThat(content.getResponse().getContentAsString()).doesNotContain("0112999888"));

        verify(profiles, never()).updateContactDetails(any(), any(), any());
    }

    @Test
    void rejectsABlankOrTooLongName() throws Exception {
        for (String body : List.of("{\"fullName\":\"   \"}", "{}", "{\"fullName\":\"" + "x".repeat(101) + "\"}")) {
            mvc.perform(put("/api/v1/users/me").with(person("nimal@example.lk", "CUSTOMER"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        }
        verify(profiles, never()).updateContactDetails(any(), any(), any());
    }
}
