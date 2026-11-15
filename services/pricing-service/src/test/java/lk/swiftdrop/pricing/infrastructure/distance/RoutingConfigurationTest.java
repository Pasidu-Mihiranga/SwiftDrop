package lk.swiftdrop.pricing.infrastructure.distance;

import com.sun.net.httpserver.HttpServer;
import lk.swiftdrop.pricing.application.port.out.RoadDistanceProvider;
import lk.swiftdrop.pricing.domain.model.GeoPoint;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class RoutingConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(RoutingConfiguration.class)
            .withBean(HaversineRoadDistanceProvider.class, () -> mock(HaversineRoadDistanceProvider.class))
            .withBean(StringRedisTemplate.class, () -> mock(StringRedisTemplate.class))
            .withBean(tools.jackson.databind.ObjectMapper.class, () -> JsonMapper.builder().build())
            .withPropertyValues(
                    "swiftdrop.pricing.routing.connect-timeout=PT0.3S",
                    "swiftdrop.pricing.routing.read-timeout=PT1S",
                    "swiftdrop.pricing.routing.cache-ttl=PT24H",
                    "swiftdrop.pricing.routing.breaker-open-for=PT30S");

    @Test
    void addsNothingWithoutARouter() {
        runner.withPropertyValues("swiftdrop.pricing.routing.url=").run(context ->
                assertThat(context).doesNotHaveBean(RoutedRoadDistanceProvider.class));
    }

    @Test
    void takesOverThePortWhenARouterIsConfigured() {
        runner.withPropertyValues("swiftdrop.pricing.routing.url=http://osrm:5000").run(context ->
                assertThat(context.getBean(RoadDistanceProvider.class))
                        .isInstanceOf(RoutedRoadDistanceProvider.class));
    }

    @Test
    void givesUpOnARouterThatAcceptsTheCallAndThenStalls() throws Exception {
        CountDownLatch finish = new CountDownLatch(1);
        HttpServer stalled = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        stalled.setExecutor(Executors.newCachedThreadPool());
        stalled.createContext("/", exchange -> {
            try {
                finish.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            exchange.close();
        });
        stalled.start();
        try {
            RoutingProperties routing = new RoutingProperties(
                    "http://127.0.0.1:" + stalled.getAddress().getPort(), Duration.ofMillis(300),
                    Duration.ofMillis(200), Duration.ofHours(24), Duration.ofSeconds(30));
            OsrmClient client = new OsrmClient(RoutingConfiguration.restClient(routing), JsonMapper.builder().build());

            long started = System.nanoTime();
            assertThatThrownBy(() -> client.drivingKm(GeoPoint.of("6.9", "79.8"), GeoPoint.of("7.2", "80.6")))
                    .isInstanceOf(RoutingUnavailableException.class);

            assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(2));
        } finally {
            finish.countDown();
            stalled.stop(0);
        }
    }
}
