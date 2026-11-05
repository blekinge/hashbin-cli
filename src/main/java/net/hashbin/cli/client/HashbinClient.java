package net.hashbin.cli.client;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import net.hashbin.cli.model.AuthResponse;
import net.hashbin.cli.model.MeView;
import net.hashbin.cli.model.TimestampRecord;

/**
 * Talks to the hashbin REST API using the JDK {@link HttpClient}. This is the
 * single point of coupling to the backend, and it is by <em>contract</em> only
 * (routes under {@code /api}, snake_case JSON, HMAC verification done elsewhere)
 * — there is no dependency on the server's classes, which is what keeps this a
 * clean sibling rather than a module of the backend.
 *
 * <p>Request bodies are built as maps so key names are exactly the wire names
 * and {@code null} optional fields are simply omitted. Responses are read into
 * {@link JsonNode} then bound to model types whose {@code @JsonNaming} handles
 * snake_case. Errors ({@code {"error": "..."}}) become {@link ApiException}.
 */
public class HashbinClient {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final String apiBase;
    private final HttpClient http;

    public HashbinClient(String apiBase) {
        this.apiBase = apiBase.endsWith("/") ? apiBase.substring(0, apiBase.length() - 1) : apiBase;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    // ── Endpoints ───────────────────────────────────────────────────────────

    /** GET /health → {@code {"status":"ok"}}. */
    public JsonNode health() {
        return exchange("GET", "/health", null, null, null);
    }

    /** POST /stamp. {@code payload} keys are the snake_case wire names. */
    public TimestampRecord stamp(Map<String, Object> payload, String token) {
        JsonNode node = exchange("POST", "/stamp", null, payload, token);
        return convert(node, TimestampRecord.class);
    }

    /** GET /verify?hash=…&algorithm=… → array of public records. */
    public List<TimestampRecord> verify(String hash, String algorithm) {
        Map<String, String> query = new LinkedHashMap<>();
        query.put("hash", hash);
        if (algorithm != null && !algorithm.isBlank() && !"auto".equalsIgnoreCase(algorithm)) {
            query.put("algorithm", algorithm);
        }
        return records(exchange("GET", "/verify", query, null, null));
    }

    /** GET /my-timestamps → array of full records (token required). */
    public List<TimestampRecord> myTimestamps(String token) {
        return records(exchange("GET", "/my-timestamps", null, null, token));
    }

    /** GET /explore?limit=…&offset=… → array of public records. */
    public List<TimestampRecord> explore(Integer limit, Integer offset) {
        Map<String, String> query = new LinkedHashMap<>();
        if (limit != null) {
            query.put("limit", limit.toString());
        }
        if (offset != null) {
            query.put("offset", offset.toString());
        }
        return records(exchange("GET", "/explore", query, null, null));
    }

    /** POST /auth/register → 201 + AuthResponse. */
    public AuthResponse register(String email, String password) {
        return convert(exchange("POST", "/auth/register", null, credentials(email, password), null),
                AuthResponse.class);
    }

    /** POST /auth/login → 200 + AuthResponse. */
    public AuthResponse login(String email, String password) {
        return convert(exchange("POST", "/auth/login", null, credentials(email, password), null),
                AuthResponse.class);
    }

    /** GET /auth/me → MeView (token required). */
    public MeView me(String token) {
        return convert(exchange("GET", "/auth/me", null, null, token), MeView.class);
    }

    // ── Plumbing ────────────────────────────────────────────────────────────

    private static Map<String, Object> credentials(String email, String password) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("email", email);
        body.put("password", password);
        return body;
    }

    private JsonNode exchange(String method, String path, Map<String, String> query,
            Map<String, Object> body, String token) {
        URI uri = URI.create(apiBase + path + queryString(query));

        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json");
        if (token != null && !token.isBlank()) {
            builder.header("Authorization", "Bearer " + token);
        }
        if ("POST".equals(method)) {
            builder.POST(HttpRequest.BodyPublishers.ofString(writeJson(body)));
        } else {
            builder.GET();
        }

        HttpResponse<String> response;
        try {
            response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            // A refused connection carries no message; show the endpoint + cause.
            String cause = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            throw new ApiException(0, "API unreachable (" + uri + "): " + cause);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException(0, "request interrupted");
        }
        return handle(response);
    }

    private JsonNode handle(HttpResponse<String> response) {
        int status = response.statusCode();
        String raw = response.body();
        try {
            JsonNode node = MAPPER.readTree(raw);
            if (status < 200 || status >= 300) {
                String message = node != null && node.hasNonNull("error")
                        ? node.get("error").asText()
                        : "HTTP " + status;
                throw new ApiException(status, message);
            }
            return node;
        } catch (IOException notJson) {
            // A gateway may return non-JSON (e.g. HTML) on 5xx.
            throw new ApiException(status, raw == null || raw.isBlank() ? "HTTP " + status : raw.trim());
        }
    }

    private String writeJson(Map<String, Object> body) {
        try {
            return MAPPER.writeValueAsString(body == null ? Map.of() : body);
        } catch (IOException e) {
            throw new IllegalStateException("failed to serialize request body", e);
        }
    }

    private <T> T convert(JsonNode node, Class<T> type) {
        try {
            return MAPPER.treeToValue(node, type);
        } catch (IOException e) {
            throw new ApiException(0, "Unexpected response shape: " + e.getMessage());
        }
    }

    private List<TimestampRecord> records(JsonNode node) {
        try {
            return MAPPER.convertValue(node, new TypeReference<List<TimestampRecord>>() {
            });
        } catch (IllegalArgumentException e) {
            throw new ApiException(0, "Unexpected response shape: " + e.getMessage());
        }
    }

    private static String queryString(Map<String, String> query) {
        if (query == null || query.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("?");
        query.forEach((k, v) -> sb.append(encode(k)).append('=').append(encode(v)).append('&'));
        sb.setLength(sb.length() - 1); // drop trailing &
        return sb.toString();
    }

    private static String encode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
