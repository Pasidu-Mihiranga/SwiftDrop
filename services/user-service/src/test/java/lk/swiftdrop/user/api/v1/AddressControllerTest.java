package lk.swiftdrop.user.api.v1;

import lk.swiftdrop.common.security.KeycloakRealmRoleConverter;
import lk.swiftdrop.common.security.SwiftDropSecurityAutoConfiguration;
import lk.swiftdrop.common.web.SwiftDropWebAutoConfiguration;
import lk.swiftdrop.common.web.error.BusinessRuleViolationException;
import lk.swiftdrop.common.web.error.ResourceNotFoundException;
import lk.swiftdrop.user.application.command.AddressBookService;
import lk.swiftdrop.user.domain.model.Address;
import lk.swiftdrop.user.domain.model.AddressDetails;
import org.junit.jupiter.api.Test;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AddressController.class)
@ImportAutoConfiguration({SwiftDropWebAutoConfiguration.class, SwiftDropSecurityAutoConfiguration.class})
class AddressControllerTest {

    private static final UUID ID = UUID.fromString("33333333-3333-4333-8333-333333333333");
    private static final String HOME_JSON =
            "{\"label\":\"Home\",\"line1\":\"No. 12, Temple Road\",\"city\":\"Kandy\",\"district\":\"Kandy\",\"postalCode\":\"20000\"}";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private AddressBookService addressBook;

    private static RequestPostProcessor person(String... roles) {
        return jwt()
                .jwt(token -> token.subject("u-1").claim("email", "nimal@x.lk")
                        .claim("realm_access", Map.of("roles", List.of(roles))))
                .authorities(new KeycloakRealmRoleConverter());
    }

    private static Address home() {
        return new Address(ID, "u-1", new AddressDetails("Home", "No. 12, Temple Road", null, "Kandy", "Kandy", "20000", null, null),
                true, Instant.parse("2026-11-13T08:00:00Z"), Instant.parse("2026-11-13T08:00:00Z"));
    }

    @Test
    void listsTheCallersAddressesUsingTheTokenSubject() throws Exception {
        when(addressBook.list("u-1")).thenReturn(List.of(home()));

        mvc.perform(get("/api/v1/users/me/addresses").with(person("CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(ID.toString()))
                .andExpect(jsonPath("$[0].line1").value("No. 12, Temple Road"))
                .andExpect(jsonPath("$[0].district").value("Kandy"))
                .andExpect(jsonPath("$[0].isDefault").value(true))
                .andExpect(jsonPath("$[0].line2").doesNotExist());
    }

    @Test
    void savesANewAddress() throws Exception {
        when(addressBook.add(any(), any(), anyBoolean())).thenReturn(home());

        mvc.perform(post("/api/v1/users/me/addresses").with(person("CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON).content(HOME_JSON))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/users/me/addresses/" + ID))
                .andExpect(jsonPath("$.label").value("Home"));
        verify(addressBook).add(any(), eq(home().details()), eq(false));
    }

    @Test
    void updatesAndCanMakeTheAddressTheDefault() throws Exception {
        when(addressBook.update(eq("u-1"), eq(ID), any(), eq(true))).thenReturn(home());

        mvc.perform(put("/api/v1/users/me/addresses/" + ID).with(person("CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(HOME_JSON.replace("}", ",\"isDefault\":true}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isDefault").value(true));
    }

    @Test
    void deletesAnAddress() throws Exception {
        mvc.perform(delete("/api/v1/users/me/addresses/" + ID).with(person("CUSTOMER")))
                .andExpect(status().isNoContent());
        verify(addressBook).delete("u-1", ID);
    }

    @Test
    void anotherPersonsAddressLooksExactlyLikeAMissingOne() throws Exception {
        doThrow(ResourceNotFoundException.of("Address", ID)).when(addressBook).delete("u-1", ID);

        mvc.perform(delete("/api/v1/users/me/addresses/" + ID).with(person("CUSTOMER")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ADDRESS_NOT_FOUND"));
    }

    @Test
    void rejectsInvalidAddressesWithoutEchoingTheStreet() throws Exception {
        for (String body : List.of(
                "{\"line1\":\"No. 12, Temple Road\",\"city\":\"Kandy\",\"district\":\"Kandy\"}",
                "{\"label\":\"Home\",\"city\":\"Kandy\",\"district\":\"Kandy\"}",
                "{\"label\":\"Home\",\"line1\":\"No. 12, Temple Road\",\"district\":\"Kandy\"}",
                "{\"label\":\"Home\",\"line1\":\"No. 12, Temple Road\",\"city\":\"Kandy\"}",
                HOME_JSON.replace("20000", "2000"),
                HOME_JSON.replace("}", ",\"lat\":95,\"lng\":80}"))) {
            mvc.perform(post("/api/v1/users/me/addresses").with(person("CUSTOMER"))
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                    .andExpect(result -> assertThat(result.getResponse().getContentAsString()).doesNotContain("Temple"));
        }
        verifyNoInteractions(addressBook);
    }

    @Test
    void rulesThatSpanFieldsAreABadRequestToo() throws Exception {
        mvc.perform(post("/api/v1/users/me/addresses").with(person("CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON).content(HOME_JSON.replace("}", ",\"lat\":7.29}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ADDRESS"));
        verifyNoInteractions(addressBook);
    }

    @Test
    void aFullAddressBookIsUnprocessable() throws Exception {
        when(addressBook.add(any(), any(), anyBoolean()))
                .thenThrow(new BusinessRuleViolationException("ADDRESS_LIMIT_REACHED", "full"));

        mvc.perform(post("/api/v1/users/me/addresses").with(person("CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON).content(HOME_JSON))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("ADDRESS_LIMIT_REACHED"));
    }

    @Test
    void onlyPeopleHaveAnAddressBook() throws Exception {
        mvc.perform(get("/api/v1/users/me/addresses")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/users/me/addresses").with(person("MERCHANT_API"))).andExpect(status().isForbidden());
        verifyNoInteractions(addressBook);
    }

    @Test
    void aBadAddressIdIsABadRequest() throws Exception {
        mvc.perform(delete("/api/v1/users/me/addresses/not-a-uuid").with(person("CUSTOMER")))
                .andExpect(status().isBadRequest());
    }
}
