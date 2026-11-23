package lk.swiftdrop.gateway.ratelimit;

import lk.swiftdrop.gateway.config.RateLimitProperties;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

/**
 * Spring Cloud Gateway's Redis token bucket with two tiers: anonymous callers ({@code ip:} keys) get
 * the lower limit, signed-in users and merchant API clients the higher one. Buckets live in Redis, so
 * every gateway replica shares them. If Redis is unreachable the request is allowed (fail open).
 *
 * <p>Redis buckets refill per second. To express "N requests a minute" exactly, each request costs
 * 60 tokens, the bucket refills N tokens a second and holds up to N requests' worth (one minute).
 */
public class TieredRedisRateLimiter extends RedisRateLimiter {

    static final String ANONYMOUS = "anonymous";
    static final String AUTHENTICATED = "authenticated";
    private static final int TOKENS_PER_REQUEST = 60;

    private final Map<String, Integer> perMinute;

    public TieredRedisRateLimiter(RateLimitProperties properties) {
        super(properties.authenticatedPerMinute(), properties.authenticatedPerMinute() * TOKENS_PER_REQUEST, TOKENS_PER_REQUEST);
        perMinute = Map.of(ANONYMOUS, properties.anonymousPerMinute(), AUTHENTICATED, properties.authenticatedPerMinute());
        perMinute.forEach((tier, limit) -> getConfig().put(tier, new Config()
                .setReplenishRate(limit)
                .setBurstCapacity(limit * TOKENS_PER_REQUEST)
                .setRequestedTokens(TOKENS_PER_REQUEST)));
    }

    @Override
    public Mono<Response> isAllowed(String routeId, String id) {
        String tier = id.startsWith(CallerKeyResolver.ANONYMOUS_PREFIX) ? ANONYMOUS : AUTHENTICATED;
        return super.isAllowed(tier, id).map(response -> response.isAllowed() ? response : withRetryAfter(response, tier));
    }

    /** A rejected caller gets back one request's worth of tokens after 60 / limit seconds. */
    private Response withRetryAfter(Response response, String tier) {
        Map<String, String> headers = new HashMap<>(response.getHeaders());
        headers.put("Retry-After", Long.toString(Math.max(1, (long) Math.ceil(60.0 / perMinute.get(tier)))));
        return new Response(false, headers);
    }
}
