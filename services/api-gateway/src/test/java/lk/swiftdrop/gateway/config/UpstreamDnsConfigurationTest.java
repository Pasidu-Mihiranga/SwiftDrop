package lk.swiftdrop.gateway.config;

import org.junit.jupiter.api.Test;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class UpstreamDnsConfigurationTest {

    @Test
    void replacesTheDefaultResolverWithOneThatForgetsAddressesQuickly() {
        HttpClient plain = HttpClient.create();

        HttpClient customised = new UpstreamDnsConfiguration()
                .upstreamDnsCacheCustomizer(Duration.ofSeconds(5)).customize(plain);

        assertThat(customised).isNotSameAs(plain);
        assertThat(customised.configuration().resolver())
                .isNotNull()
                .isNotSameAs(plain.configuration().resolver());
    }
}
