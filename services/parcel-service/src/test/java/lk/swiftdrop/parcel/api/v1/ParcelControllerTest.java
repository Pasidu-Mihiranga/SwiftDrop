package lk.swiftdrop.parcel.api.v1;

import lk.swiftdrop.common.security.KeycloakRealmRoleConverter;
import lk.swiftdrop.common.security.SwiftDropSecurityAutoConfiguration;
import lk.swiftdrop.common.web.SwiftDropWebAutoConfiguration;
import lk.swiftdrop.common.web.error.ResourceNotFoundException;
import lk.swiftdrop.events.common.Types.ActorRole;
import lk.swiftdrop.events.common.Types.ParcelStatus;
import lk.swiftdrop.parcel.ParcelFixtures;
import lk.swiftdrop.parcel.application.command.CancelParcelUseCase;
import lk.swiftdrop.parcel.application.command.CreateParcelCommand;
import lk.swiftdrop.parcel.application.command.CreateParcelUseCase;
import lk.swiftdrop.parcel.application.exception.PricingUnavailableException;
import lk.swiftdrop.parcel.application.model.PagedResult;
import lk.swiftdrop.parcel.application.query.ParcelQueryService;
import lk.swiftdrop.parcel.domain.exception.ParcelNotCancellableException;
import lk.swiftdrop.parcel.domain.exception.QuoteExpiredException;
import lk.swiftdrop.parcel.domain.model.Parcel;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ParcelController.class)
@ImportAutoConfiguration({SwiftDropWebAutoConfiguration.class, SwiftDropSecurityAutoConfiguration.class})
class ParcelControllerTest {

    private static final UUID QUOTE_ID = UUID.randomUUID();
    private static final String BOOKING = """
            {"quoteId":"%s",
             "pickup":{"line1":"12 Galle Road","city":"Colombo 03","district":"Colombo","postalCode":"00300"},
             "dropoff":{"line1":"45 Peradeniya Road","line2":"Flat 2","city":"Kandy","district":"Kandy"},
             "recipient":{"name":"Saman Perera","phone":"0712345678","email":"saman@example.com"},
             "notes":"Leave at reception"}
            """.formatted(QUOTE_ID);

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;
    @MockitoBean
    private CreateParcelUseCase createParcel;
    @MockitoBean
    private CancelParcelUseCase cancelParcel;
    @MockitoBean
    private ParcelQueryService parcels;

    private static RequestPostProcessor as(String userId, String role) {
        return jwt().jwt(token -> token.subject(userId).claim("realm_access", Map.of("roles", List.of(role))))
                .authorities(new KeycloakRealmRoleConverter());
    }

    @Test
    void aCustomerBooksAndReceivesTheTrackingNumber() throws Exception {
        Parcel booked = ParcelFixtures.bookedBy("cust-1");
        when(createParcel.create(any(), any())).thenReturn(booked);

        mvc.perform(post("/api/v1/parcels").with(as("cust-1", "CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON).content(BOOKING))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/v1/parcels/" + booked.id())))
                .andExpect(jsonPath("$.id").value(booked.id().toString()))
                .andExpect(jsonPath("$.trackingNumber").value(booked.trackingNumber().value()))
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.fee.amount").value(650.00))
                .andExpect(jsonPath("$.fee.currency").value("LKR"))
                .andExpect(jsonPath("$.dropoff.city").value("Kandy"))
                .andExpect(jsonPath("$.recipient.phone").value("0712345678"))
                .andExpect(jsonPath("$.history[0].toStatus").value("CREATED"))
                .andExpect(jsonPath("$.history[0].actorId").doesNotExist());

        ArgumentCaptor<CreateParcelCommand> command = ArgumentCaptor.forClass(CreateParcelCommand.class);
        verify(createParcel).create(any(), command.capture());
        assertThat(command.getValue().quoteId()).isEqualTo(QUOTE_ID);
        assertThat(command.getValue().dropoff().line2()).isEqualTo("Flat 2");
        assertThat(command.getValue().recipient().name()).isEqualTo("Saman Perera");
    }

    @Test
    void couriersMerchantsAndStaffCannotCreateACustomerBooking() throws Exception {
        for (String role : List.of("COURIER", "MERCHANT", "MERCHANT_API", "DISPATCHER", "ADMIN")) {
            mvc.perform(post("/api/v1/parcels").with(as("user-1", role))
                            .contentType(MediaType.APPLICATION_JSON).content(BOOKING))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        }
        verifyNoInteractions(createParcel);
    }

    @Test
    void anonymousCallersAreRejected() throws Exception {
        mvc.perform(post("/api/v1/parcels").contentType(MediaType.APPLICATION_JSON).content(BOOKING))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/parcels/mine")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/parcels/" + UUID.randomUUID())).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/parcels/" + UUID.randomUUID() + "/cancel")).andExpect(status().isUnauthorized());
    }

    @Test
    void anInvalidBodyIsRejectedWithoutEchoingPersonalData() throws Exception {
        String invalid = """
                {"pickup":{"line1":"12 Galle Road","city":"","district":"Colombo","postalCode":"3"},
                 "dropoff":{"line1":"45 Peradeniya Road","city":"Kandy","district":"Kandy"},
                 "recipient":{"name":"Saman Perera","phone":"011-2345678"}}
                """;

        mvc.perform(post("/api/v1/parcels").with(as("cust-1", "CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON).content(invalid))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[*].field").value(hasItem("quoteId")))
                .andExpect(jsonPath("$.errors[*].field").value(hasItem("pickup.city")))
                .andExpect(jsonPath("$.errors[*].field").value(hasItem("pickup.postalCode")))
                .andExpect(jsonPath("$.errors[*].field").value(hasItem("recipient.phone")))
                .andExpect(content().string(not(containsString("011-2345678"))));
        verifyNoInteractions(createParcel);
    }

    @Test
    void anExpiredQuoteIsAClearBusinessError() throws Exception {
        when(createParcel.create(any(), any())).thenThrow(new QuoteExpiredException());

        mvc.perform(post("/api/v1/parcels").with(as("cust-1", "CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON).content(BOOKING))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("QUOTE_EXPIRED"))
                .andExpect(jsonPath("$.detail").value(containsString("expired")));
    }

    @Test
    void whenPricingIsDownTheAnswerIsServiceUnavailable() throws Exception {
        when(createParcel.create(any(), any()))
                .thenThrow(new PricingUnavailableException(new IllegalStateException("connect timed out to 10.0.0.7")));

        mvc.perform(post("/api/v1/parcels").with(as("cust-1", "CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON).content(BOOKING))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("PRICING_UNAVAILABLE"))
                .andExpect(content().string(not(containsString("10.0.0.7"))));
    }

    @Test
    void listsTheCallersParcelsWithoutHistory() throws Exception {
        Parcel mine = ParcelFixtures.bookedBy("cust-1");
        when(parcels.mine(any(), eq(ParcelStatus.CREATED), eq(1), eq(5)))
                .thenReturn(new PagedResult<>(List.of(mine), 1, 5, 6, 2));

        mvc.perform(get("/api/v1/parcels/mine?page=1&size=5&status=CREATED").with(as("cust-1", "CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].trackingNumber").value(mine.trackingNumber().value()))
                .andExpect(jsonPath("$.content[0].history").doesNotExist())
                .andExpect(jsonPath("$.page.page").value(1))
                .andExpect(jsonPath("$.page.size").value(5))
                .andExpect(jsonPath("$.page.totalElements").value(6))
                .andExpect(jsonPath("$.page.totalPages").value(2));
    }

    @Test
    void listDefaultsToTheFirstTwentyAndRejectsBadPaging() throws Exception {
        when(parcels.mine(any(), eq(null), eq(0), eq(20))).thenReturn(new PagedResult<>(List.of(), 0, 20, 0, 0));

        mvc.perform(get("/api/v1/parcels/mine").with(as("cust-1", "CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());
        mvc.perform(get("/api/v1/parcels/mine?size=500").with(as("cust-1", "CUSTOMER")))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/parcels/mine?page=-1").with(as("cust-1", "CUSTOMER")))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/parcels/mine?status=LOST").with(as("cust-1", "CUSTOMER")))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/parcels/mine").with(as("courier-1", "COURIER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void opensAParcelWithItsHistory() throws Exception {
        Parcel parcel = ParcelFixtures.bookedBy("cust-1");
        when(parcels.get(any(), eq(parcel.id()))).thenReturn(parcel);

        mvc.perform(get("/api/v1/parcels/" + parcel.id()).with(as("cust-1", "CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(parcel.id().toString()))
                .andExpect(jsonPath("$.pickup.line1").value("12 Galle Road"))
                .andExpect(jsonPath("$.history.length()").value(1));
    }

    @Test
    void aParcelTheCallerMayNotSeeIsNotFound() throws Exception {
        UUID id = UUID.randomUUID();
        when(parcels.get(any(), eq(id))).thenThrow(ResourceNotFoundException.of("Parcel", id));

        mvc.perform(get("/api/v1/parcels/" + id).with(as("cust-2", "CUSTOMER")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PARCEL_NOT_FOUND"));
        mvc.perform(get("/api/v1/parcels/not-a-uuid").with(as("cust-2", "CUSTOMER")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void cancelsAndReportsAParcelThatCannotBeCancelled() throws Exception {
        Parcel created = ParcelFixtures.bookedBy("cust-1");
        Parcel cancelled = created.cancel("cust-1", ActorRole.CUSTOMER, ParcelFixtures.NOW.plusSeconds(60));
        UUID pickedUp = UUID.randomUUID();
        when(cancelParcel.cancel(any(), eq(created.id()))).thenReturn(cancelled);
        when(cancelParcel.cancel(any(), eq(pickedUp))).thenThrow(new ParcelNotCancellableException());

        mvc.perform(post("/api/v1/parcels/" + created.id() + "/cancel").with(as("cust-1", "CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.history[1].fromStatus").value("CREATED"))
                .andExpect(jsonPath("$.history[1].toStatus").value("CANCELLED"));
        mvc.perform(post("/api/v1/parcels/" + pickedUp + "/cancel").with(as("cust-1", "CUSTOMER")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("PARCEL_NOT_CANCELLABLE"));
    }
}
