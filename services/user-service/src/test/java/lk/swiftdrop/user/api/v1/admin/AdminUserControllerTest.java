package lk.swiftdrop.user.api.v1.admin;

import lk.swiftdrop.common.security.KeycloakRealmRoleConverter;
import lk.swiftdrop.common.security.SwiftDropSecurityAutoConfiguration;
import lk.swiftdrop.common.web.SwiftDropWebAutoConfiguration;
import lk.swiftdrop.common.web.error.BusinessRuleViolationException;
import lk.swiftdrop.common.web.error.ConflictException;
import lk.swiftdrop.user.application.command.AdminUserService;
import lk.swiftdrop.user.application.model.ManagedUser;
import lk.swiftdrop.user.application.model.NewStaffAccount;
import lk.swiftdrop.user.application.model.UserPage;
import lk.swiftdrop.user.application.query.AdminUserQueryService;
import lk.swiftdrop.user.domain.model.UserRole;
import lk.swiftdrop.user.domain.model.VehicleType;
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
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminUserController.class)
@ImportAutoConfiguration({SwiftDropWebAutoConfiguration.class, SwiftDropSecurityAutoConfiguration.class})
class AdminUserControllerTest {

    private static final UUID KANDY = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final ManagedUser KASUN =
            new ManagedUser("u-2", "kasun@x.lk", "Kasun Silva", true, Set.of(UserRole.COURIER, UserRole.CUSTOMER));

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private AdminUserQueryService queries;

    @MockitoBean
    private AdminUserService commands;

    private static RequestPostProcessor caller(String... roles) {
        return jwt()
                .jwt(token -> token.subject("admin-1").claim("realm_access", Map.of("roles", List.of(roles))))
                .authorities(new KeycloakRealmRoleConverter());
    }

    @Test
    void anAdminSearchesAccounts() throws Exception {
        when(queries.search("kasun", UserRole.COURIER, true, 1, 10)).thenReturn(new UserPage(List.of(KASUN), 11, 1, 10));

        mvc.perform(get("/api/v1/admin/users").param("search", "kasun").param("role", "COURIER")
                        .param("enabled", "true").param("page", "1").param("size", "10").with(caller("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value("u-2"))
                .andExpect(jsonPath("$.items[0].roles[0]").value("COURIER"))
                .andExpect(jsonPath("$.items[0].roles[1]").value("CUSTOMER"))
                .andExpect(jsonPath("$.total").value(11))
                .andExpect(jsonPath("$.page").value(1));
    }

    @Test
    void onlyAdminsManageAccounts() throws Exception {
        for (String role : List.of("DISPATCHER", "COURIER", "CUSTOMER", "MERCHANT", "MERCHANT_API")) {
            mvc.perform(get("/api/v1/admin/users").with(caller(role))).andExpect(status().isForbidden());
            mvc.perform(post("/api/v1/admin/users").with(caller(role)).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"a@x.lk\",\"fullName\":\"A\",\"role\":\"COURIER\"}")).andExpect(status().isForbidden());
        }
        mvc.perform(get("/api/v1/admin/users")).andExpect(status().isUnauthorized());
        verifyNoInteractions(queries, commands);
    }

    @Test
    void createsACourierWithDetailsAndReportsTheInvitation() throws Exception {
        when(commands.create(any())).thenReturn(new AdminUserService.CreatedAccount(KASUN, true));

        mvc.perform(post("/api/v1/admin/users").with(caller("ADMIN")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"kasun@x.lk\",\"fullName\":\" Kasun Silva \",\"phone\":\"0771234567\",\"role\":\"COURIER\","
                                + "\"vehicleType\":\"BIKE\",\"homeZoneId\":\"" + KANDY + "\",\"maxDailyCapacity\":25}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/admin/users/u-2"))
                .andExpect(jsonPath("$.user.email").value("kasun@x.lk"))
                .andExpect(jsonPath("$.invitationSent").value(true));

        ArgumentCaptor<NewStaffAccount> account = ArgumentCaptor.forClass(NewStaffAccount.class);
        verify(commands).create(account.capture());
        assertThat(account.getValue().fullName()).isEqualTo("Kasun Silva");
        assertThat(account.getValue().phone().value()).isEqualTo("+94771234567");
        assertThat(account.getValue().courier()).isEqualTo(new NewStaffAccount.CourierDetails(VehicleType.BIKE, KANDY, 25));
    }

    @Test
    void rejectsInvalidNewAccounts() throws Exception {
        for (String body : List.of(
                "{\"fullName\":\"A\",\"role\":\"COURIER\"}",
                "{\"email\":\"not-an-email\",\"fullName\":\"A\",\"role\":\"COURIER\"}",
                "{\"email\":\"a@x.lk\",\"role\":\"COURIER\"}",
                "{\"email\":\"a@x.lk\",\"fullName\":\"A\"}",
                "{\"email\":\"a@x.lk\",\"fullName\":\"A\",\"role\":\"COURIER\",\"phone\":\"0112345678\"}",
                "{\"email\":\"a@x.lk\",\"fullName\":\"A\",\"role\":\"COURIER\",\"maxDailyCapacity\":500}")) {
            mvc.perform(post("/api/v1/admin/users").with(caller("ADMIN")).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        }
        mvc.perform(post("/api/v1/admin/users").with(caller("ADMIN")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"a@x.lk\",\"fullName\":\"A\",\"role\":\"COURIER\",\"vehicleType\":\"BIKE\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COURIER_DETAILS_INCOMPLETE"));
        verifyNoInteractions(commands);
    }

    @Test
    void aTakenEmailIsAConflict() throws Exception {
        when(commands.create(any())).thenThrow(new ConflictException("EMAIL_ALREADY_REGISTERED", "exists"));

        mvc.perform(post("/api/v1/admin/users").with(caller("ADMIN")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"a@x.lk\",\"fullName\":\"A\",\"role\":\"DISPATCHER\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"));
    }

    @Test
    void updatesAnAccountAsTheCallingAdmin() throws Exception {
        when(commands.update(eq("admin-1"), eq("u-2"), eq(false), eq(Set.of(UserRole.DISPATCHER)))).thenReturn(
                new ManagedUser("u-2", "kasun@x.lk", "Kasun Silva", false, Set.of(UserRole.DISPATCHER, UserRole.CUSTOMER)));

        mvc.perform(patch("/api/v1/admin/users/u-2").with(caller("ADMIN")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false,\"roles\":[\"DISPATCHER\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));
    }

    @Test
    void selfLockoutIsUnprocessable() throws Exception {
        when(commands.update(eq("admin-1"), eq("admin-1"), eq(false), any()))
                .thenThrow(new BusinessRuleViolationException("CANNOT_DISABLE_YOURSELF", "no"));

        mvc.perform(patch("/api/v1/admin/users/admin-1").with(caller("ADMIN")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("CANNOT_DISABLE_YOURSELF"));
    }

    @Test
    void resendsTheInvitation() throws Exception {
        mvc.perform(post("/api/v1/admin/users/u-2/invitation").with(caller("ADMIN"))).andExpect(status().isAccepted());
        verify(commands).resendInvitation("u-2");
    }
}
