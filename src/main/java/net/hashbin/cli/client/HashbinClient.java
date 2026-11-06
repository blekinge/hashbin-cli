package net.hashbin.cli.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import net.hashbin.api.AuthApi;
import net.hashbin.api.HealthResourceApi;
import net.hashbin.api.TimestampsApi;
import net.hashbin.api.model.AuthRequest;
import net.hashbin.cli.config.CliConfig;
import net.hashbin.cli.model.AuthResponse;
import net.hashbin.cli.model.TimestampRecord;
import org.eclipse.microprofile.rest.client.annotation.RegisterProvider;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jspecify.annotations.Nullable;

/**
 * Talks to the hashbin REST API through the client <em>generated</em> from the backend's OpenAPI
 * contract and consumed as a Maven dependency ({@code net.hashbin:hashbin-api} → {@code
 * net.hashbin.api.*}, a MicroProfile REST Client interface plus Jackson models).
 *
 * <p>It is an {@link ApplicationScoped} facade injecting three configured REST clients (one per
 * generated interface), each declared as a local sub-interface below that {@code extends} the
 * generated {@code *Api} and adds, without editing it:
 *
 * <ul>
 *   <li>{@code @RegisterRestClient(configKey="hashbin")} — so {@code @RestClient} builds a proxy
 *       against {@code quarkus.rest-client.hashbin.url};
 *   <li>{@code @RegisterProvider} ({@link ConditionalBearerFilter}) — where an interface has
 *       token-bearing endpoints — adds {@code Authorization: Bearer} from the resolved {@link
 *       CliConfig} only to those endpoints (a per-client {@code ClientHeadersFactory} cannot see
 *       the request path, so it would stamp every call); {@code HashbinHealth} omits it, being
 *       anonymous-only;
 *   <li>{@code @RegisterProvider} ({@link BaseUriOverrideFilter}) — rewrites the request origin to
 *       {@code --api-base}, overriding the startup-bound config URL at request time so the runtime
 *       base URL is honoured.
 * </ul>
 *
 * Non-2xx is mapped to {@code net.hashbin.api.ApiException} by the generated, globally
 * {@code @Provider}-registered {@code ApiExceptionMapper}; {@link #guard} translates it (and
 * transport failures) into the CLI's own {@link ApiException}. The facade's remaining job is
 * adapting the generated DTOs to the CLI's stable models.
 *
 * <p>Two endpoints ({@code /health}, {@code /auth/login}) are declared with a loose schema in the
 * backend's OpenAPI document and so arrive as a raw {@code Object} (a Jackson {@code Map}); the
 * facade reads the fields it needs from that map.
 */
@ApplicationScoped
public class HashbinClient {

    /** Only used to read the {@code {"error": "..."}} message out of failed responses. */
    private static final ObjectMapper ERROR_MAPPER = new ObjectMapper();

    @Inject CliConfig config;

    private final HashbinTimestamps timestamps;
    private final HashbinAuth auth;
    private final HashbinHealth health;

    @Inject
    HashbinClient(
            @RestClient HashbinTimestamps timestamps,
            @RestClient HashbinAuth auth,
            @RestClient HashbinHealth health) {
        this.timestamps = timestamps;
        this.auth = auth;
        this.health = health;
    }

    // ── Configured clients: decorate the generated interfaces (we don't own them) ───

    @Singleton
    @RegisterRestClient(configKey = "hashbin")
    @RegisterProvider(BaseUriOverrideFilter.class)
    @RegisterProvider(ConditionalBearerFilter.class)
    interface HashbinTimestamps extends TimestampsApi {}

    @Singleton
    @RegisterRestClient(configKey = "hashbin")
    @RegisterProvider(BaseUriOverrideFilter.class)
    @RegisterProvider(ConditionalBearerFilter.class)
    interface HashbinAuth extends AuthApi {}

    @Singleton
    @RegisterRestClient(configKey = "hashbin")
    @RegisterProvider(BaseUriOverrideFilter.class)
    // No bearer filter: /health is the only operation here and it is always anonymous.
    interface HashbinHealth extends HealthResourceApi {}

    // ── Endpoints ───────────────────────────────────────────────────────────

    /** GET /health → the server's {@code status} value (typically {@code "ok"}). */
    public String health() {
        return guard(health::apiHealthGet).getStatus();
    }

    /** POST /stamp. {@code payload} keys are the snake_case wire names. */
    public TimestampRecord stamp(net.hashbin.cli.model.StampRequest payload) {
        return TimestampRecord.map(
                guard(
                        () ->
                                timestamps.apiStampPost(
                                        net.hashbin.cli.model.StampRequest.toStampRequest(payload),
                                        null,
                                        null)));
    }

    /** GET /verify?hash=…&algorithm=… → array of public records. */
    public List<TimestampRecord> verify(String hash, String algorithm) {
        return guard(() -> timestamps.apiVerifyGet(algorithmOrNull(algorithm), hash)).stream()
                .map(TimestampRecord::map)
                .toList();
    }

    private static @Nullable String algorithmOrNull(final String algorithm) {
        return (algorithm == null || algorithm.isBlank() || "auto".equalsIgnoreCase(algorithm))
                ? null
                : algorithm;
    }

    /** GET /my-timestamps → array of full records (bearer token applied by the header factory). */
    public List<TimestampRecord> myTimestamps() {
        return guard(timestamps::apiMyTimestampsGet).stream().map(TimestampRecord::map).toList();
    }

    /** GET /explore?limit=…&offset=… → array of public records. */
    public List<TimestampRecord> explore(Integer limit, Integer offset) {
        return guard(() -> timestamps.apiExploreGet(stringOrNull(limit), stringOrNull(offset)))
                .stream()
                .map(TimestampRecord::map)
                .toList();
    }

    private static @Nullable String stringOrNull(final Integer value) {
        return Optional.ofNullable(value).map(Object::toString).orElse(null);
    }

    /** POST /auth/register → 201 + AuthResponse. */
    public AuthResponse register(String email, String password) {
        return AuthResponse.map(
                guard(() -> auth.apiAuthRegisterPost(toAuthRequest(email, password))));
    }

    /** POST /auth/login → 200 + AuthResponse (loose schema: parsed from the raw body map). */
    public AuthResponse login(String email, String password) {
        return AuthResponse.map(guard(() -> auth.apiAuthLoginPost(toAuthRequest(email, password))));
    }

    /** GET /auth/me → MeView (bearer token applied by the header factory). */
    public net.hashbin.cli.model.MeView me() {
        return net.hashbin.cli.model.MeView.map(guard(auth::apiAuthMeGet));
    }

    // ── Model adaptation ────────────────────────────────────────────────────

    private static AuthRequest toAuthRequest(String email, String password) {
        AuthRequest request = new AuthRequest();
        request.setEmail(email);
        request.setPassword(password);
        return request;
    }

    // ── Error mapping ───────────────────────────────────────────────────────

    /**
     * Runs a generated call and translates its exceptions into the CLI's {@link ApiException}: HTTP
     * errors carry the server's {@code {"error": ...}} message verbatim, transport failures surface
     * as status 0 with the endpoint.
     */
    private <T> T guard(Supplier<T> call) {
        try {
            return call.get();
        } catch (net.hashbin.api.ApiException e) {
            throw translate(e);
        } catch (RuntimeException e) {
            // No HTTP response produced: connection refused / DNS failure / interrupted.
            String cause = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            throw new ApiException(0, "API unreachable (" + config.apiBase() + "): " + cause);
        }
    }

    private ApiException translate(net.hashbin.api.ApiException e) {
        Response response = e.getResponse();
        int status = response != null ? response.getStatus() : 0;
        String body = readEntity(response);
        if (status == 0) {
            String cause = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            return new ApiException(0, "API unreachable (" + config.apiBase() + "): " + cause);
        }
        String message = errorMessage(body);
        if (message == null) {
            message = body == null || body.isBlank() ? "HTTP " + status : body.trim();
        }
        return new ApiException(status, message);
    }

    private static String readEntity(Response response) {
        if (response == null) {
            return null;
        }
        try {
            return response.readEntity(String.class);
        } catch (RuntimeException unreadable) {
            return null;
        }
    }

    /** Extracts the {@code error} field from a {@code {"error": "..."}} body, or {@code null}. */
    private static String errorMessage(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            JsonNode node = ERROR_MAPPER.readTree(body);
            return node != null && node.hasNonNull("error") ? node.get("error").asText() : null;
        } catch (Exception notJson) {
            return null; // a gateway may return HTML on 5xx; caller falls back to the raw body
        }
    }
}
