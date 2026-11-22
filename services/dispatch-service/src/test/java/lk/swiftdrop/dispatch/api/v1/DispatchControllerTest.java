package lk.swiftdrop.dispatch.api.v1;

import lk.swiftdrop.common.security.SwiftDropSecurityAutoConfiguration;
import lk.swiftdrop.common.web.SwiftDropWebAutoConfiguration;
import lk.swiftdrop.dispatch.application.DispatchService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.time.LocalDate;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DispatchController.class)
@ImportAutoConfiguration({SwiftDropWebAutoConfiguration.class,SwiftDropSecurityAutoConfiguration.class})
class DispatchControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean JwtDecoder decoder;
    @MockitoBean DispatchService service;
    private org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor caller(String role) {
        return jwt().jwt(j -> j.subject("courier1")).authorities(new SimpleGrantedAuthority("ROLE_"+role));
    }
    @ParameterizedTest @ValueSource(strings={"COURIER","CUSTOMER","MERCHANT"})
    void nonDispatchRolesCannotReadOrMutateDispatch(String role) throws Exception {
        for (String path : List.of("unassigned","workloads","assignments"))
            mvc.perform(get("/api/v1/dispatch/"+path).with(caller(role))).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/dispatch/assignments").with(caller(role)).contentType("application/json")
            .content("{\"parcelId\":\""+UUID.randomUUID()+"\",\"courierId\":\"c2\"}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/dispatch/assignments/"+UUID.randomUUID()+"/reassign").with(caller(role))
            .contentType("application/json").content("{\"courierId\":\"c2\"}")).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
    @ParameterizedTest @ValueSource(strings={"DISPATCHER","ADMIN"})
    void staffCanReadAndAllocate(String role) throws Exception {
        mvc.perform(get("/api/v1/dispatch/unassigned?zone=CP&page=1&size=5").with(caller(role))).andExpect(status().isOk());
        verify(service).unassigned("CP",1,5);
        mvc.perform(get("/api/v1/dispatch/workloads").with(caller(role))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/dispatch/assignments?date=2026-11-22").with(caller(role))).andExpect(status().isOk());
        UUID parcel = UUID.randomUUID(), assignment = UUID.randomUUID();
        mvc.perform(post("/api/v1/dispatch/assignments").with(caller(role)).contentType("application/json")
            .content("{\"parcelId\":\""+parcel+"\",\"courierId\":\"c2\"}")).andExpect(status().isCreated());
        verify(service).assign(eq(parcel),eq("c2"),isNull(),eq("courier1"),eq(role.equals("ADMIN")),anyString());
        mvc.perform(post("/api/v1/dispatch/assignments/"+assignment+"/reassign").with(caller(role)).contentType("application/json")
            .content("{\"courierId\":\"c2\"}")).andExpect(status().isOk());
        verify(service).reassign(eq(assignment),eq("c2"),eq("courier1"),eq(role.equals("ADMIN")),anyString());
    }
    @Test void runSheetIgnoresRequestedCourierAndUsesTokenSubject() throws Exception {
        mvc.perform(get("/api/v1/courier/run-sheet?date=2026-11-22&courierId=other").with(caller("COURIER"))).andExpect(status().isOk());
        verify(service).runs(LocalDate.of(2026,11,22),"courier1");
        mvc.perform(get("/api/v1/courier/run-sheet").with(caller("COURIER"))).andExpect(status().isOk());
        verify(service).runs(null,"courier1");
    }
    @Test void validatesPaginationAndAllocationBody() throws Exception {
        for (String query : List.of("page=-1","size=0","size=101"))
            mvc.perform(get("/api/v1/dispatch/unassigned?"+query).with(caller("DISPATCHER"))).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/dispatch/assignments").with(caller("ADMIN")).contentType("application/json")
            .content("{\"courierId\":\" \"}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/dispatch/assignments/"+UUID.randomUUID()+"/reassign").with(caller("ADMIN"))
            .contentType("application/json").content("{\"courierId\":\"\"}")).andExpect(status().isBadRequest());
    }
    @Test void anonymousAndWrongRoleCannotReadRunSheets() throws Exception {
        mvc.perform(get("/api/v1/courier/run-sheet")).andExpect(status().isUnauthorized());
        for (String role : List.of("CUSTOMER","MERCHANT","DISPATCHER","ADMIN"))
            mvc.perform(get("/api/v1/courier/run-sheet").with(caller(role))).andExpect(status().isForbidden());
    }
}
