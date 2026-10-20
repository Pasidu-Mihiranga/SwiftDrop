package lk.swiftdrop.common.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lk.swiftdrop.common.web.correlation.CorrelationId;
import lk.swiftdrop.common.web.correlation.CorrelationIdFilter;
import lk.swiftdrop.common.web.error.BusinessRuleViolationException;
import lk.swiftdrop.common.web.error.ConflictException;
import lk.swiftdrop.common.web.error.GlobalExceptionHandler;
import lk.swiftdrop.common.web.error.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    record CreateRequest(@NotBlank String name, @Pattern(regexp = "0\\d{9}", message = "must be a valid phone number") String phone) { }

    @RestController
    static class TestController {
        @GetMapping("/parcels/{id}")
        String find(@PathVariable String id) {
            throw ResourceNotFoundException.of("Parcel", id);
        }

        @GetMapping("/conflict")
        String conflict() {
            throw new ConflictException("MERCHANT_ALREADY_REGISTERED", "This business is already registered");
        }

        @GetMapping("/rule")
        String rule() {
            throw new BusinessRuleViolationException("INVALID_STATUS_TRANSITION", "Cannot move from CREATED to DELIVERED");
        }

        @GetMapping("/boom")
        String boom() {
            throw new IllegalStateException("db password=hunter2 for 0771234567");
        }

        @PostMapping("/create")
        String create(@Valid @RequestBody CreateRequest request) {
            return "ok";
        }
    }

    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new TestController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .addFilters(new CorrelationIdFilter())
            .build();

    @Test
    void apiExceptionsBecomeProblemDetails() throws Exception {
        mvc.perform(get("/parcels/42").header(CorrelationId.HEADER, "corr-12345678"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(header().string(CorrelationId.HEADER, "corr-12345678"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("PARCEL_NOT_FOUND"))
                .andExpect(jsonPath("$.type").value("https://api.swiftdrop.lk/problems/parcel-not-found"))
                .andExpect(jsonPath("$.traceId").value("corr-12345678"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void conflictAndBusinessRuleStatuses() throws Exception {
        mvc.perform(get("/conflict")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MERCHANT_ALREADY_REGISTERED"));
        mvc.perform(get("/rule")).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INVALID_STATUS_TRANSITION"));
    }

    @Test
    void validationErrorsListFieldsButNeverEchoRejectedValues() throws Exception {
        mvc.perform(post("/create").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"phone\":\"12345\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors", hasSize(2)))
                .andExpect(content().string(not(containsString("12345"))));
    }

    @Test
    void unexpectedErrorsHideInternalDetails() throws Exception {
        mvc.perform(get("/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(content().string(not(containsString("hunter2"))))
                .andExpect(content().string(not(containsString("0771234567"))));
    }

    @Test
    void springMvcErrorsAreEnrichedToo() throws Exception {
        mvc.perform(post("/create").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    void invalidIncomingCorrelationIdIsReplaced() throws Exception {
        mvc.perform(get("/parcels/1").header(CorrelationId.HEADER, "bad id\nINJECTED"))
                .andExpect(header().string(CorrelationId.HEADER, not(containsString("INJECTED"))))
                .andExpect(header().string(CorrelationId.HEADER, not(startsWith("bad"))));
    }
}
