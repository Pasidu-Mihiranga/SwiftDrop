package lk.swiftdrop.parcel;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;

/**
 * A stand-in for pricing-service's {@code GET /api/v1/pricing/quotes/{quoteId}} on a random local
 * port. A quote that was not registered answers 404, exactly like an unknown or expired quote.
 */
public final class PricingStub implements AutoCloseable {

    /** What the stub saw of one call. {@code body} is the request body; empty for a GET. */
    public record Call(String path, String authorization, String correlationId, String body) { }

    private record Answer(int status, String body, Duration delay) { }

    private final HttpServer server;
    private final Map<String, Answer> answers = new ConcurrentHashMap<>();
    /** The answer to POST /api/v1/pricing/quotes (merchant orders); a valid quote unless changed. */
    private volatile Answer newQuoteAnswer = null;
    private final List<Call> calls = new CopyOnWriteArrayList<>();

    public PricingStub() {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        server.setExecutor(Executors.newCachedThreadPool());
        server.createContext("/api/v1/pricing/quotes/", this::handle);
        server.createContext("/api/v1/pricing/quotes", this::handleNewQuote);
        server.start();
    }

    public String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    /** A valid quote for 2 kg, size S, Colombo to Kandy, LKR 650.00. */
    public void validQuote(UUID quoteId, Instant expiresAt) {
        answers.put(quoteId.toString(), new Answer(200, quoteJson(quoteId, expiresAt), Duration.ZERO));
    }

    public void answer(UUID quoteId, int status, String body) {
        answers.put(quoteId.toString(), new Answer(status, body, Duration.ZERO));
    }

    public void slowQuote(UUID quoteId, Duration delay) {
        answers.put(quoteId.toString(),
                new Answer(200, quoteJson(quoteId, Instant.now().plusSeconds(600)), delay));
    }

    public List<Call> calls() {
        return List.copyOf(calls);
    }

    /** POST /api/v1/pricing/quotes answers with this status and body from now on. */
    public void answerNewQuotes(int status, String body) {
        newQuoteAnswer = new Answer(status, body, Duration.ZERO);
    }

    /** POST /api/v1/pricing/quotes answers with a valid quote, after a delay. */
    public void slowNewQuotes(Duration delay) {
        newQuoteAnswer = new Answer(200, null, delay);
    }

    public void reset() {
        answers.clear();
        calls.clear();
        newQuoteAnswer = null;
    }

    public static String quoteJson(UUID quoteId, Instant expiresAt) {
        return """
                {"quoteId":"%s","fee":{"amount":650.00,"currency":"LKR"},
                 "breakdown":[{"rule":"BASE","amount":500.00},{"rule":"WEIGHT","amount":150.00}],
                 "fromZone":"WP-COL","toZone":"CP-KDY","fromDistrict":"Colombo","toDistrict":"Kandy",
                 "weightKg":2.00,"sizeClass":"S","serviceLevel":"STANDARD","codAmount":0,
                 "issuedAt":"%s","expiresAt":"%s","weightBasis":"ACTUAL"}
                """.formatted(quoteId, expiresAt.minusSeconds(900), expiresAt);
    }

    private void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        record(exchange, path);
        Answer answer = answers.getOrDefault(path.substring(path.lastIndexOf('/') + 1),
                new Answer(404, "{\"code\":\"QUOTE_NOT_FOUND\"}", Duration.ZERO));
        respond(exchange, answer);
    }

    /** A fresh quote each time (new id), as pricing-service issues one per request. */
    private void handleNewQuote(HttpExchange exchange) throws IOException {
        record(exchange, exchange.getRequestURI().getPath());
        Answer configured = newQuoteAnswer;
        Answer answer = configured != null && configured.body() != null ? configured
                : new Answer(200, quoteJson(UUID.randomUUID(), Instant.now().plusSeconds(900)),
                        configured == null ? Duration.ZERO : configured.delay());
        respond(exchange, answer);
    }

    private void record(HttpExchange exchange, String path) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        calls.add(new Call(path, exchange.getRequestHeaders().getFirst("Authorization"),
                exchange.getRequestHeaders().getFirst("X-Correlation-Id"), body));
    }

    private static void respond(HttpExchange exchange, Answer answer) {
        try {
            Thread.sleep(answer.delay().toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        byte[] body = answer.body().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        try (exchange) {
            exchange.sendResponseHeaders(answer.status(), body.length);
            exchange.getResponseBody().write(body);
        } catch (IOException clientGaveUp) {
            // the caller timed out and closed the connection; nothing left to send
        }
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
