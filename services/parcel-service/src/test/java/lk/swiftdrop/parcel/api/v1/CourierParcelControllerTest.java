package lk.swiftdrop.parcel.api.v1;

import lk.swiftdrop.common.security.KeycloakRealmRoleConverter;
import lk.swiftdrop.common.security.SwiftDropSecurityAutoConfiguration;
import lk.swiftdrop.common.web.SwiftDropWebAutoConfiguration;
import lk.swiftdrop.events.common.Types.ActorRole;
import lk.swiftdrop.events.common.Types.ParcelStatus;
import lk.swiftdrop.outbox.OutboxWriter;
import lk.swiftdrop.parcel.ParcelFixtures;
import lk.swiftdrop.parcel.application.command.DeliveryProofUseCase;
import lk.swiftdrop.parcel.application.command.UpdateParcelStatusUseCase;
import lk.swiftdrop.parcel.application.model.PagedResult;
import lk.swiftdrop.parcel.application.port.out.DeliveryProofStore;
import lk.swiftdrop.parcel.application.port.out.ParcelRepository;
import lk.swiftdrop.parcel.application.port.out.ProofStorage;
import lk.swiftdrop.parcel.application.port.out.ProofStorage.PresignedUrl;
import lk.swiftdrop.parcel.application.query.ParcelQueryService;
import lk.swiftdrop.parcel.domain.model.Parcel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import lk.swiftdrop.parcel.application.port.out.ParcelMetrics;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The courier and dispatch endpoints with the real use cases and access policy; only the ports are
 * mocked, so the status codes come from the same code paths as in production.
 */
@WebMvcTest({CourierParcelController.class, DispatchParcelController.class})
@ImportAutoConfiguration({SwiftDropWebAutoConfiguration.class, SwiftDropSecurityAutoConfiguration.class})
@Import({UpdateParcelStatusUseCase.class, DeliveryProofUseCase.class, ParcelQueryService.class,
        CourierParcelControllerTest.FixedClock.class})
class CourierParcelControllerTest {

    private static final Instant LATER = ParcelFixtures.NOW.plusSeconds(3600);

    @TestConfiguration
    static class FixedClock {
        @Bean
        Clock clock() {
            return Clock.fixed(LATER, ZoneOffset.UTC);
        }
    }

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;
    @MockitoBean
    private ParcelRepository parcels;
    @MockitoBean
    private DeliveryProofStore proofs;
    @MockitoBean
    private ProofStorage storage;
    @MockitoBean
    private OutboxWriter outbox;
    @MockitoBean
    private ParcelMetrics metrics;

    private Parcel assigned;

    @BeforeEach
    void setUp() {
        assigned = ParcelFixtures.bookedBy("cust-1").assignCourier("courier-1", ParcelFixtures.NOW);
        lenient().when(parcels.findById(assigned.id())).thenReturn(Optional.of(assigned));
        lenient().when(parcels.save(any())).thenAnswer(call -> call.getArgument(0));
    }

    private static RequestPostProcessor as(String userId, String role) {
        return jwt().jwt(token -> token.subject(userId).claim("realm_access", Map.of("roles", List.of(role))))
                .authorities(new KeycloakRealmRoleConverter());
    }

    private static String update(String toStatus) {
        return "{\"toStatus\":\"" + toStatus + "\"}";
    }

    private Parcel outForDelivery() {
        Parcel parcel = assigned;
        for (ParcelStatus step : List.of(ParcelStatus.PICKED_UP, ParcelStatus.IN_TRANSIT, ParcelStatus.OUT_FOR_DELIVERY)) {
            parcel = parcel.changeStatus(step, "courier-1", ActorRole.COURIER, null, null, null, null, LATER);
        }
        when(parcels.findById(parcel.id())).thenReturn(Optional.of(parcel));
        return parcel;
    }

    @Test
    void theAssignedCourierMovesTheParcelOn() throws Exception {
        mvc.perform(patch("/api/v1/courier/parcels/" + assigned.id() + "/status").with(as("courier-1", "COURIER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"toStatus\":\"PICKED_UP\",\"note\":\"Collected\",\"location\":{\"lat\":6.9,\"lng\":79.85}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PICKED_UP"))
                .andExpect(jsonPath("$.history.length()").value(2))
                .andExpect(jsonPath("$.history[1].note").value("Collected"));
        verify(outbox).publish(any());
    }

    @Test
    void courierBGets404ForCourierAsParcel() throws Exception {
        mvc.perform(patch("/api/v1/courier/parcels/" + assigned.id() + "/status").with(as("courier-2", "COURIER"))
                        .contentType(MediaType.APPLICATION_JSON).content(update("PICKED_UP")))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/courier/parcels/" + assigned.id() + "/proof-upload-url").with(as("courier-2", "COURIER"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"contentType\":\"image/png\",\"sizeBytes\":100}"))
                .andExpect(status().isNotFound());
        verify(parcels, never()).save(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"CUSTOMER", "MERCHANT", "MERCHANT_API"})
    void customersAndMerchantsCannotCallCourierEndpoints(String role) throws Exception {
        mvc.perform(patch("/api/v1/courier/parcels/" + assigned.id() + "/status").with(as("cust-1", role))
                        .contentType(MediaType.APPLICATION_JSON).content(update("PICKED_UP")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/courier/parcels").with(as("cust-1", role))).andExpect(status().isForbidden());
    }

    @Test
    void anonymousCallersMustSignIn() throws Exception {
        mvc.perform(patch("/api/v1/courier/parcels/" + assigned.id() + "/status")
                        .contentType(MediaType.APPLICATION_JSON).content(update("PICKED_UP")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createdToDeliveredIsRejectedWithAClearMessage() throws Exception {
        mvc.perform(patch("/api/v1/courier/parcels/" + assigned.id() + "/status").with(as("courier-1", "COURIER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"toStatus\":\"DELIVERED\",\"proof\":{\"type\":\"NAME\",\"receivedBy\":\"Saman\"}}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INVALID_STATUS_TRANSITION"))
                .andExpect(jsonPath("$.detail").value("A parcel cannot move from CREATED to DELIVERED."));
    }

    @Test
    void failedWithoutAReasonIsRejectedAndWithOneSucceeds() throws Exception {
        Parcel out = outForDelivery();

        mvc.perform(patch("/api/v1/courier/parcels/" + out.id() + "/status").with(as("courier-1", "COURIER"))
                        .contentType(MediaType.APPLICATION_JSON).content(update("FAILED")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("FAILURE_REASON_REQUIRED"));
        mvc.perform(patch("/api/v1/courier/parcels/" + out.id() + "/status").with(as("courier-1", "COURIER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"toStatus\":\"FAILED\",\"failureReason\":\"REFUSED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.history[4].failureReason").value("REFUSED"));
    }

    @Test
    void deliveredNeedsProof() throws Exception {
        Parcel out = outForDelivery();

        mvc.perform(patch("/api/v1/courier/parcels/" + out.id() + "/status").with(as("courier-1", "COURIER"))
                        .contentType(MediaType.APPLICATION_JSON).content(update("DELIVERED")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("DELIVERY_PROOF_REQUIRED"));
        mvc.perform(patch("/api/v1/courier/parcels/" + out.id() + "/status").with(as("courier-1", "COURIER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"toStatus\":\"DELIVERED\",\"proof\":{\"type\":\"NAME\",\"receivedBy\":\"Saman\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DELIVERED"));
    }

    @Test
    void malformedRequestsAreBadRequests() throws Exception {
        mvc.perform(patch("/api/v1/courier/parcels/" + assigned.id() + "/status").with(as("courier-1", "COURIER"))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(patch("/api/v1/courier/parcels/" + assigned.id() + "/status").with(as("courier-1", "COURIER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"toStatus\":\"PICKED_UP\",\"location\":{\"lat\":120,\"lng\":0}}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void theCourierGetsAnUploadLinkAndTheirParcelList() throws Exception {
        when(storage.presignUpload(anyString(), eq("image/jpeg"), eq(1234L)))
                .thenReturn(new PresignedUrl("https://s3.local/put", LATER.plusSeconds(600)));
        when(parcels.findByCourier("courier-1", null, 0, 50))
                .thenReturn(new PagedResult<>(List.of(assigned), 0, 50, 1, 1));

        mvc.perform(post("/api/v1/courier/parcels/" + assigned.id() + "/proof-upload-url").with(as("courier-1", "COURIER"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"contentType\":\"image/jpeg\",\"sizeBytes\":1234}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uploadUrl").value("https://s3.local/put"))
                .andExpect(jsonPath("$.proofId").isNotEmpty());
        mvc.perform(get("/api/v1/courier/parcels").with(as("courier-1", "COURIER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(assigned.id().toString()))
                .andExpect(jsonPath("$.page.totalElements").value(1));
    }

    @Test
    void anyoneWhoMaySeeTheParcelGetsAProofLink() throws Exception {
        UUID proofId = UUID.randomUUID();
        when(proofs.findById(proofId)).thenReturn(Optional.of(new DeliveryProofStore.StoredProof(proofId, assigned.id(),
                "proofs/p.png", "image/png", 10, LATER, LATER)));
        when(storage.presignDownload("proofs/p.png")).thenReturn(new PresignedUrl("https://s3.local/get", LATER));

        mvc.perform(get("/api/v1/courier/parcels/" + assigned.id() + "/proofs/" + proofId).with(as("cust-1", "CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("https://s3.local/get"));
        mvc.perform(get("/api/v1/courier/parcels/" + assigned.id() + "/proofs/" + proofId).with(as("cust-2", "CUSTOMER")))
                .andExpect(status().isNotFound());
    }

    @Test
    void dispatchReattemptsAFailedDeliveryAndCouriersCannot() throws Exception {
        Parcel failed = outForDelivery().changeStatus(ParcelStatus.FAILED, "courier-1", ActorRole.COURIER, null,
                lk.swiftdrop.events.common.Types.FailureReason.WRONG_ADDRESS, null, null, LATER);
        when(parcels.findById(failed.id())).thenReturn(Optional.of(failed));

        mvc.perform(post("/api/v1/parcels/" + failed.id() + "/reattempt").with(as("courier-1", "COURIER")))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/v1/courier/parcels/" + failed.id() + "/status").with(as("courier-1", "COURIER"))
                        .contentType(MediaType.APPLICATION_JSON).content(update("OUT_FOR_DELIVERY")))
                .andExpect(status().isUnprocessableContent());
        mvc.perform(post("/api/v1/parcels/" + failed.id() + "/reattempt").with(as("disp-1", "DISPATCHER"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"Call before arriving\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OUT_FOR_DELIVERY"));
    }
}
