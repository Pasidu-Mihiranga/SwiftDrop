package lk.swiftdrop.dispatch.infrastructure;

import lk.swiftdrop.dispatch.application.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
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
    public List<Courier> couriers(String token) {
        var result = client.get().uri("/api/v1/admin/couriers").headers(h -> h.setBearerAuth(token))
            .retrieve().body(new ParameterizedTypeReference<List<Courier>>() { });
        return result == null ? List.of() : result;
    }
}
