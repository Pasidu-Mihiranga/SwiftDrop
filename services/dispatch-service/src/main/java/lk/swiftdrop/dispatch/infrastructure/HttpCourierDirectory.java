package lk.swiftdrop.dispatch.infrastructure;

import lk.swiftdrop.common.web.error.ApiException;
import lk.swiftdrop.dispatch.application.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import java.util.List;
import static lk.swiftdrop.dispatch.application.DispatchModels.*;

@Component
public class HttpCourierDirectory implements CourierDirectory {
    private final RestClient client;
    public HttpCourierDirectory(@Value("${swiftdrop.dispatch.user-service-url:http://localhost:8081}") String url) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2000); factory.setReadTimeout(5000);
        client = RestClient.builder().baseUrl(url).requestFactory(factory).build();
    }

    /**
     * The couriers user-service knows of. When it cannot answer (down, slow, or its own identity
     * provider failing) this is reported as 503, so the board says "try again" instead of 500.
     */
    public List<Courier> couriers(String token) {
        try {
            var result = client.get().uri("/api/v1/admin/couriers").headers(h -> h.setBearerAuth(token))
                .retrieve().body(new ParameterizedTypeReference<List<Courier>>() { });
            return result == null ? List.of() : result;
        } catch (HttpServerErrorException | ResourceAccessException unavailable) {
            throw new CourierDirectoryUnavailableException(unavailable);
        }
    }

    /** user-service could not list the couriers. Answered as 503 problem+json. */
    public static class CourierDirectoryUnavailableException extends ApiException {
        CourierDirectoryUnavailableException(Throwable cause) {
            super(HttpStatus.SERVICE_UNAVAILABLE, "COURIER_DIRECTORY_UNAVAILABLE",
                "The courier list is unavailable just now. Please try again shortly.");
            initCause(cause);
        }
    }
}
