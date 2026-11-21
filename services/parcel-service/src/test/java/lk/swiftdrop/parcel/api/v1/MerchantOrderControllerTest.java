package lk.swiftdrop.parcel.api.v1;

import lk.swiftdrop.common.security.AuthenticatedUser;
import lk.swiftdrop.common.security.KeycloakRealmRoleConverter;
import lk.swiftdrop.common.security.SwiftDropSecurityAutoConfiguration;
import lk.swiftdrop.common.web.SwiftDropWebAutoConfiguration;
import lk.swiftdrop.common.web.error.BadRequestException;
import lk.swiftdrop.common.web.error.ConflictException;
import lk.swiftdrop.events.common.Types.ParcelStatus;
import lk.swiftdrop.events.common.Types.ServiceLevel;
import lk.swiftdrop.events.common.Types.SizeClass;
import lk.swiftdrop.parcel.OpenApiContract;
import lk.swiftdrop.parcel.ParcelFixtures;
import lk.swiftdrop.parcel.application.command.CancelParcelUseCase;
import lk.swiftdrop.parcel.application.command.CreateParcelUseCase;
import lk.swiftdrop.parcel.application.command.MerchantOrderCommand;
import lk.swiftdrop.parcel.application.command.MerchantOrderService;
import lk.swiftdrop.parcel.application.exception.MerchantAccessDeniedException;
import lk.swiftdrop.parcel.application.exception.OrderNotPriceableException;
import lk.swiftdrop.parcel.application.exception.PricingUnavailableException;
import lk.swiftdrop.parcel.application.model.MerchantOrderFilter;
import lk.swiftdrop.parcel.application.model.MerchantOrderResult;
import lk.swiftdrop.parcel.application.model.PagedResult;
import lk.swiftdrop.parcel.application.query.ParcelQueryService;
import lk.swiftdrop.parcel.domain.exception.IdempotencyKeyReusedException;
import lk.swiftdrop.parcel.domain.model.IdempotencyKey;
import lk.swiftdrop.parcel.domain.model.Parcel;
import lk.swiftdrop.parcel.domain.model.TrackingNumber;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
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

/** The merchant order API's HTTP behaviour and access rules, next to the customer API it must not open up. */
@WebMvcTest({MerchantOrderController.class, ParcelController.class})
@ImportAutoConfiguration({SwiftDropWebAutoConfiguration.class, SwiftDropSecurityAutoConfiguration.class})
class MerchantOrderControllerTest {

    static final String MERCHANT = "00000000-0000-4000-8000-0000000000a1";
    static final String ORDERS = "/api/v1/merchant/orders";
    static final String KEY = "3f1c2a9e-8b7d-4c6e-9f01-23456789abcd";
    static final String ORDER = """
            {"merchantOrderRef":" ORD-1001 ",
             "pickup":{"line1":"12 Galle Road","city":"Colombo 03","district":"Colombo","postalCode":"00300"},
             "dropoff":{"line1":"45 Peradeniya Road","line2":"Flat 2","city":"Kandy","district":"Kandy"},
             "recipient":{"name":"Saman Perera","phone":"0712345678","email":"saman@example.com"},
             "weightKg":2.5,"sizeClass":"S","serviceLevel":"EXPRESS","codAmount":4500}
            """;

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;
    @MockitoBean
    private MerchantOrderService orders;
    @MockitoBean
    private CreateParcelUseCase createParcel;
    @MockitoBean
    private CancelParcelUseCase cancelParcel;
    @MockitoBean
    private ParcelQueryService parcels;

    static RequestPostProcessor as(String userId, String merchantId, String... roles) {
        return jwt().jwt(token -> {
            token.subject(userId).claim("realm_access", Map.of("roles", List.of(roles)));
            if (merchantId != null) {
                token.claim("merchant_id", merchantId);
            }
        }).authorities(new KeycloakRealmRoleConverter());
    }

    static RequestPostProcessor merchantSystem() {
        return as("service-account-merchant-a1", MERCHANT, "MERCHANT_API");
    }

    private static Parcel order() {
        Parcel booked = Parcel.bookForMerchant(TrackingNumber.of(LocalDate.of(2026, 11, 20), "M3RCH7"), MERCHANT,
                "ORD-1001", "service-account-merchant-a1", ParcelFixtures.details(), ParcelFixtures.NOW);
        return new Parcel(booked.id(), booked.trackingNumber(), booked.source(), booked.customerId(),
                booked.merchantId(), booked.merchantOrderRef(), booked.details(), booked.status(), booked.courierId(),
                booked.history(), booked.createdAt(), booked.updatedAt(), 0L);
    }

    private static MockHttpServletRequestBuilder create(String body) {
        return post(ORDERS).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private ResultActions createAsMerchant(String key, String body) throws Exception {
        MockHttpServletRequestBuilder request = create(body).with(merchantSystem());
        return mvc.perform(key == null ? request : request.header("Idempotency-Key", key));
    }

    private static String body(MvcResult result) throws Exception {
        return result.getResponse().getContentAsString();
    }

    // --- create

    @Test
    void aNewOrderIs201WithItsLocationAndMatchesTheContract() throws Exception {
        Parcel created = order();
        when(orders.create(any(), any(), any())).thenReturn(new MerchantOrderResult(created, false));

        MvcResult result = createAsMerchant(KEY, ORDER)
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith("/api/v1/parcels/" + created.id())))
                .andExpect(header().doesNotExist(MerchantOrderController.REPLAYED))
                .andExpect(jsonPath("$.source").value("MERCHANT"))
                .andExpect(jsonPath("$.merchantOrderRef").value("ORD-1001"))
                .andExpect(jsonPath("$.trackingNumber").value(created.trackingNumber().value()))
                .andExpect(jsonPath("$.fee.amount").value(650.00))
                .andReturn();

        OpenApiContract.assertParcelSchema("MerchantOrder", body(result));
    }

    @Test
    void theRequestBecomesACommandAndTheMerchantComesFromTheToken() throws Exception {
        when(orders.create(any(), any(), any())).thenReturn(new MerchantOrderResult(order(), false));

        createAsMerchant(KEY, ORDER).andExpect(status().isCreated());

        ArgumentCaptor<AuthenticatedUser> caller = ArgumentCaptor.forClass(AuthenticatedUser.class);
        ArgumentCaptor<IdempotencyKey> key = ArgumentCaptor.forClass(IdempotencyKey.class);
        ArgumentCaptor<MerchantOrderCommand> command = ArgumentCaptor.forClass(MerchantOrderCommand.class);
        verify(orders).create(caller.capture(), key.capture(), command.capture());
        assertThat(caller.getValue().merchantId()).contains(MERCHANT);
        assertThat(key.getValue().value()).isEqualTo(KEY);
        assertThat(command.getValue().merchantOrderRef()).as("trimmed").isEqualTo("ORD-1001");
        assertThat(command.getValue().weightKg()).isEqualByComparingTo("2.5");
        assertThat(command.getValue().sizeClass()).isEqualTo(SizeClass.S);
        assertThat(command.getValue().serviceLevel()).isEqualTo(ServiceLevel.EXPRESS);
        assertThat(command.getValue().codAmount()).isEqualByComparingTo("4500");
        assertThat(command.getValue().pickup().district()).isEqualTo("Colombo");
    }

    @Test
    void aMerchantIdInTheBodyOrQueryIsIgnored() throws Exception {
        when(orders.create(any(), any(), any())).thenReturn(new MerchantOrderResult(order(), false));
        String withOtherMerchant = ORDER.replaceFirst("\\{",
                "{\"merchantId\":\"00000000-0000-4000-8000-0000000000b2\",");

        mvc.perform(post(ORDERS + "?merchantId=00000000-0000-4000-8000-0000000000b2").with(merchantSystem())
                        .header("Idempotency-Key", KEY).contentType(MediaType.APPLICATION_JSON).content(withOtherMerchant))
                .andExpect(status().isCreated());

        verify(orders).create(org.mockito.ArgumentMatchers.argThat(user -> user.merchantId().orElseThrow()
                .equals(MERCHANT)), any(), any());
    }

    @Test
    void aReplayIs200WithTheOriginalOrderAndAReplayHeader() throws Exception {
        Parcel original = order();
        when(orders.create(any(), any(), any())).thenReturn(new MerchantOrderResult(original, true));

        MvcResult result = createAsMerchant(KEY, ORDER)
                .andExpect(status().isOk())
                .andExpect(header().string(MerchantOrderController.REPLAYED, "true"))
                .andExpect(header().string("Location", endsWith("/api/v1/parcels/" + original.id())))
                .andExpect(jsonPath("$.id").value(original.id().toString()))
                .andReturn();

        OpenApiContract.assertParcelSchema("MerchantOrder", body(result));
    }

    // --- the Idempotency-Key header

    @Test
    void aMissingIdempotencyKeyIs400AndNothingHappens() throws Exception {
        MvcResult result = createAsMerchant(null, ORDER)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_INVALID"))
                .andReturn();

        OpenApiContract.assertProblem(body(result));
        verifyNoInteractions(orders);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "short", "has a space in it", "ключ-ключ-ключ"})
    void anInvalidIdempotencyKeyIs400(String key) throws Exception {
        createAsMerchant(key, ORDER)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_INVALID"));
        verifyNoInteractions(orders);
    }

    @Test
    void aKeyLongerThan100CharactersIs400() throws Exception {
        createAsMerchant("k".repeat(101), ORDER).andExpect(status().isBadRequest());
        verifyNoInteractions(orders);
    }

    // --- an invalid body

    @Test
    void everyInvalidFieldIsReportedAndNothingHappens() throws Exception {
        String invalid = """
                {"merchantOrderRef":" ","pickup":{"line1":"","city":"Colombo 03","district":"Colombo"},
                 "recipient":{"name":"Saman","phone":"12345"},"weightKg":0,"sizeClass":"S","codAmount":-1}
                """;

        MvcResult result = createAsMerchant(KEY, invalid)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[*].field", hasItem("merchantOrderRef")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("pickup.line1")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("dropoff")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("recipient.phone")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("weightKg")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("codAmount")))
                .andReturn();

        OpenApiContract.assertProblem(body(result));
        verifyNoInteractions(orders);
    }

    @Test
    void anUnknownSizeClassOrMalformedJsonIs400() throws Exception {
        createAsMerchant(KEY, ORDER.replace("\"S\"", "\"XXL\"")).andExpect(status().isBadRequest());
        createAsMerchant(KEY, "{not json").andExpect(status().isBadRequest());
        verifyNoInteractions(orders);
    }

    @Test
    void aTooHeavyParcelIs400() throws Exception {
        createAsMerchant(KEY, ORDER.replace("\"weightKg\":2.5", "\"weightKg\":100.5"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", hasItem("weightKg")));
    }

    // --- refusals

    @Test
    void aMerchantThatIsNotApprovedIs403() throws Exception {
        when(orders.create(any(), any(), any())).thenThrow(MerchantAccessDeniedException.notApproved());

        MvcResult result = createAsMerchant(KEY, ORDER)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MERCHANT_NOT_APPROVED"))
                .andReturn();
        OpenApiContract.assertProblem(body(result));
    }

    @Test
    void aTokenWithoutAMerchantIs403() throws Exception {
        when(orders.create(any(), any(), any())).thenThrow(MerchantAccessDeniedException.noMerchant());

        mvc.perform(create(ORDER).with(as("service-account-x", null, "MERCHANT_API")).header("Idempotency-Key", KEY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MERCHANT_ID_MISSING"));
    }

    @Test
    void reusingAKeyForADifferentOrderIs409() throws Exception {
        when(orders.create(any(), any(), any())).thenThrow(new IdempotencyKeyReusedException());

        MvcResult result = createAsMerchant(KEY, ORDER)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"))
                .andReturn();
        OpenApiContract.assertProblem(body(result));
    }

    @Test
    void anUnfinishedConcurrentRequestIs409WithItsOwnCode() throws Exception {
        when(orders.create(any(), any(), any())).thenThrow(new ConflictException("IDEMPOTENCY_KEY_IN_PROGRESS", "retry"));

        createAsMerchant(KEY, ORDER)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_IN_PROGRESS"));
    }

    @Test
    void anOrderPricingRefusesIs422WithPricingsCode() throws Exception {
        when(orders.create(any(), any(), any()))
                .thenThrow(new OrderNotPriceableException("DISTRICT_NOT_SERVICED", "Mullaitivu is not serviced."));

        MvcResult result = createAsMerchant(KEY, ORDER)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("DISTRICT_NOT_SERVICED"))
                .andExpect(jsonPath("$.detail").value("Mullaitivu is not serviced."))
                .andReturn();
        OpenApiContract.assertProblem(body(result));
    }

    @Test
    void pricingBeingDownIs503() throws Exception {
        when(orders.create(any(), any(), any()))
                .thenThrow(new PricingUnavailableException(new RuntimeException("timeout")));

        MvcResult result = createAsMerchant(KEY, ORDER)
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("PRICING_UNAVAILABLE"))
                .andReturn();
        OpenApiContract.assertProblem(body(result));
    }

    // --- who may call what

    @Test
    void anAnonymousCallerIs401() throws Exception {
        mvc.perform(create(ORDER).header("Idempotency-Key", KEY)).andExpect(status().isUnauthorized());
        mvc.perform(get(ORDERS)).andExpect(status().isUnauthorized());
        verifyNoInteractions(orders);
    }

    @ParameterizedTest
    @ValueSource(strings = {"CUSTOMER", "DISPATCHER", "COURIER", "ADMIN", "MERCHANT"})
    void onlyAMerchantsSystemMayCreateOrders(String role) throws Exception {
        mvc.perform(create(ORDER).with(as("user-1", MERCHANT, role)).header("Idempotency-Key", KEY))
                .andExpect(status().isForbidden());
        verifyNoInteractions(orders);
    }

    @ParameterizedTest
    @ValueSource(strings = {"CUSTOMER", "DISPATCHER", "COURIER", "ADMIN"})
    void peopleWhoAreNotMerchantsCannotListMerchantOrders(String role) throws Exception {
        mvc.perform(get(ORDERS).with(as("user-1", MERCHANT, role))).andExpect(status().isForbidden());
        verifyNoInteractions(orders);
    }

    @Test
    void aMerchantSystemCannotUseTheCustomerOnlyApi() throws Exception {
        // a complete, valid booking, so the request reaches the authorization check
        String booking = """
                {"quoteId":"%s",
                 "pickup":{"line1":"12 Galle Road","city":"Colombo 03","district":"Colombo"},
                 "dropoff":{"line1":"45 Peradeniya Road","city":"Kandy","district":"Kandy"},
                 "recipient":{"name":"Saman Perera","phone":"0712345678"}}
                """.formatted(java.util.UUID.randomUUID());
        mvc.perform(post("/api/v1/parcels").with(merchantSystem()).contentType(MediaType.APPLICATION_JSON)
                        .content(booking))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/parcels/mine").with(merchantSystem())).andExpect(status().isForbidden());
        verifyNoInteractions(createParcel, parcels);
    }

    // --- listing

    @Test
    void bothTheMerchantsSystemAndItsPeopleCanListAndTheResultMatchesTheContract() throws Exception {
        when(orders.list(any(), any(), anyInt(), anyInt()))
                .thenReturn(new PagedResult<>(List.of(order()), 0, 20, 1, 1));

        for (RequestPostProcessor caller : List.of(merchantSystem(), as("user-merchant", MERCHANT, "MERCHANT"))) {
            MvcResult result = mvc.perform(get(ORDERS).with(caller))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].source").value("MERCHANT"))
                    .andExpect(jsonPath("$.content[0].history").doesNotExist())
                    .andExpect(jsonPath("$.page.totalElements").value(1))
                    .andReturn();
            OpenApiContract.assertParcelSchema("MerchantOrderPage", body(result));
        }
    }

    @Test
    void filtersAndPagingReachTheServiceAndAMerchantIdParameterIsIgnored() throws Exception {
        when(orders.list(any(), any(), anyInt(), anyInt())).thenReturn(new PagedResult<>(List.of(), 2, 5, 11, 3));

        mvc.perform(get(ORDERS).with(merchantSystem())
                        .param("status", "DELIVERED")
                        .param("from", "2026-11-01T00:00:00Z")
                        .param("to", "2026-11-20T00:00:00Z")
                        .param("page", "2").param("size", "5")
                        .param("merchantId", "00000000-0000-4000-8000-0000000000b2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.page").value(2))
                .andExpect(jsonPath("$.page.totalPages").value(3));

        ArgumentCaptor<AuthenticatedUser> caller = ArgumentCaptor.forClass(AuthenticatedUser.class);
        ArgumentCaptor<MerchantOrderFilter> filter = ArgumentCaptor.forClass(MerchantOrderFilter.class);
        verify(orders).list(caller.capture(), filter.capture(), eq(2), eq(5));
        assertThat(caller.getValue().merchantId()).contains(MERCHANT);
        assertThat(filter.getValue()).isEqualTo(new MerchantOrderFilter(ParcelStatus.DELIVERED,
                Instant.parse("2026-11-01T00:00:00Z"), Instant.parse("2026-11-20T00:00:00Z")));
    }

    @Test
    void theDefaultsAreTheFirstPageOf20WithNoFilter() throws Exception {
        when(orders.list(any(), any(), anyInt(), anyInt())).thenReturn(new PagedResult<>(List.of(), 0, 20, 0, 0));

        mvc.perform(get(ORDERS).with(merchantSystem())).andExpect(status().isOk());

        verify(orders).list(any(), eq(MerchantOrderFilter.none()), eq(0), eq(20));
    }

    @Test
    void invalidPagingOrFiltersAre400() throws Exception {
        List<MockHttpServletRequestBuilder> invalid = new ArrayList<>(List.of(
                get(ORDERS).param("size", "101"), get(ORDERS).param("size", "0"), get(ORDERS).param("page", "-1"),
                get(ORDERS).param("status", "LOST"), get(ORDERS).param("from", "yesterday")));
        for (MockHttpServletRequestBuilder request : invalid) {
            mvc.perform(request.with(merchantSystem())).andExpect(status().isBadRequest());
        }
        verifyNoInteractions(orders);
    }

    @Test
    void aDateRangeTheServiceRefusesIs400() throws Exception {
        when(orders.list(any(), any(), anyInt(), anyInt()))
                .thenThrow(new BadRequestException("INVALID_DATE_RANGE", "'from' must be before 'to'."));

        mvc.perform(get(ORDERS).with(merchantSystem()).param("from", "2026-11-20T00:00:00Z")
                        .param("to", "2026-11-01T00:00:00Z"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_DATE_RANGE"));
    }
}
