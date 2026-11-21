package lk.swiftdrop.dispatch.infrastructure;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;
class HttpCourierDirectoryTest {
    @Test void forwardsBearerTokenAndReadsM1DirectoryShape() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/api/v1/admin/couriers",exchange -> {
            assertEquals("Bearer staff-token",exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] body = "[{\"id\":\"c1\",\"fullName\":\"Courier One\",\"email\":\"not-projected@example.test\",\"homeZoneId\":\"zone1\",\"maxDailyCapacity\":20,\"assignable\":true}]".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type","application/json");
            exchange.sendResponseHeaders(200,body.length); exchange.getResponseBody().write(body); exchange.close();
        });
        server.start();
        try {
            var directory = new HttpCourierDirectory("http://127.0.0.1:"+server.getAddress().getPort());
            var result = directory.couriers("staff-token");
            assertEquals("Courier One",result.getFirst().fullName()); assertTrue(result.getFirst().assignable());
            assertEquals(20,result.getFirst().maxDailyCapacity());
        } finally { server.stop(0); }
    }

    @Test void reportsAFailingUserServiceAs503() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/api/v1/admin/couriers",exchange -> {
            byte[] body = "{\"code\":\"IDENTITY_PROVIDER_UNAVAILABLE\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type","application/json");
            exchange.sendResponseHeaders(503,body.length); exchange.getResponseBody().write(body); exchange.close();
        });
        server.start();
        try {
            var directory = new HttpCourierDirectory("http://127.0.0.1:"+server.getAddress().getPort());
            var error = assertThrows(HttpCourierDirectory.CourierDirectoryUnavailableException.class, () -> directory.couriers("staff-token"));
            assertEquals(503, error.status().value()); assertEquals("COURIER_DIRECTORY_UNAVAILABLE", error.code());
        } finally { server.stop(0); }
    }
    @Test void reportsAnUnreachableUserServiceAs503() throws Exception {
        int unused;
        try (var socket = new java.net.ServerSocket(0)) { unused = socket.getLocalPort(); }
        var directory = new HttpCourierDirectory("http://127.0.0.1:"+unused);
        assertThrows(HttpCourierDirectory.CourierDirectoryUnavailableException.class, () -> directory.couriers("staff-token"));
    }
}
