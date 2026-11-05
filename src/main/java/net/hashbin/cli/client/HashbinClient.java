package net.hashbin.cli.client;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.ws.rs.core.Response;

import org.eclipse.microprofile.rest.client.RestClientBuilder;

import net.hashbin.api.ApiExceptionMapper;
import net.hashbin.api.AuthApi;
import net.hashbin.api.HealthResourceApi;
import net.hashbin.api.TimestampsApi;
import net.hashbin.api.auth.BearerClientHeadersFactory;
import net.hashbin.api.model.AuthRequest;
import net.hashbin.api.model.MeView;
import net.hashbin.api.model.StampRequest;
import net.hashbin.api.model.TimestampPublicView;
import net.hashbin.api.model.TimestampView;
import net.hashbin.cli.model.AuthResponse;
import net.hashbin.cli.model.TimestampRecord;

/**
 * Talks to the hashbin REST API through the client <em>generated</em> from the
 * backend's OpenAPI contract and consumed as a Maven dependency
 * ({@code net.hashbin:hashbin-api} → {@code net.hashbin.api.*}, a MicroProfile
 * REST Client interface plus Jackson models).
 *
 * <p>This class is a thin <em>facade</em>: it owns the two things the generated
 * library cannot know about — the runtime base URL and the per-call bearer token —
 * and adapts the generated types to the CLI's own stable models
 * ({@link TimestampRecord}, {@link AuthResponse}, {@link net.hashbin.cli.model.MeView})
 * and its {@link ApiException}. Routing, (de)serialization and transport all live in
 * the generated {@code *Api} interfaces, wired here through the MicroProfile
 * {@link RestClientBuilder}, so the wire contract is never hand-coded here.
 *
 * <p>Two endpoints are declared with a loose schema in the backend's OpenAPI
 * document and so arrive as a raw {@code Object} (a Jackson {@code Map}):
 * {@code /health} and {@code /auth/login}. The facade reads the fields it needs
 * out of that map rather than assuming a generated type exists for them.
 */
public class HashbinClient {

    /** Only used to read the {@code {"error": "..."}} message out of failed responses. */
    private static final ObjectMapper ERROR_MAPPER = new ObjectMapper();

    private final String apiBase;

    public HashbinClient(String apiBase) {
        this.apiBase = apiBase.endsWith("/") ? apiBase.substring(0, apiBase.length() - 1) : apiBase;
    }

    // ── Endpoints ───────────────────────────────────────────────────────────

    /** GET /health → the server's {@code status} value (typically {@code "ok"}). */
    public String health() {
        Object body = guard(() -> api(HealthResourceApi.class, null).apiHealthGet());
        return textField(body, "status");
    }

    /** POST /stamp. {@code payload} keys are the snake_case wire names. */
    public TimestampRecord stamp(Map<String, Object> payload, String token) {
        TimestampView view = guard(() -> api(TimestampsApi.class, token)
                .apiStampPost(toStampRequest(payload), null, null));
        return toRecord(view);
    }

    /** GET /verify?hash=…&algorithm=… → array of public records. */
    public List<TimestampRecord> verify(String hash, String algorithm) {
        String algo = (algorithm == null || algorithm.isBlank() || "auto".equalsIgnoreCase(algorithm))
                ? null
                : algorithm;
        List<TimestampPublicView> views = guard(() -> api(TimestampsApi.class, null).apiVerifyGet(algo, hash));
        return views.stream().map(HashbinClient::toRecord).toList();
    }

    /** GET /my-timestamps → array of full records (token required). */
    public List<TimestampRecord> myTimestamps(String token) {
        List<TimestampView> views = guard(() -> api(TimestampsApi.class, token).apiMyTimestampsGet());
        return views.stream().map(HashbinClient::toRecord).toList();
    }

    /** GET /explore?limit=…&offset=… → array of public records. */
    public List<TimestampRecord> explore(Integer limit, Integer offset) {
        String limitParam = limit == null ? null : limit.toString();
        String offsetParam = offset == null ? null : offset.toString();
        List<TimestampPublicView> views =
                guard(() -> api(TimestampsApi.class, null).apiExploreGet(limitParam, offsetParam));
        return views.stream().map(HashbinClient::toRecord).toList();
    }

    /** POST /auth/register → 201 + AuthResponse. */
    public AuthResponse register(String email, String password) {
        net.hashbin.api.model.AuthResponse r =
                guard(() -> api(AuthApi.class, null).apiAuthRegisterPost(toAuthRequest(email, password)));
        return toAuthResponse(r.getToken(), r.getTokenType(), r.getExpiresIn(), r.getUserId(), r.getEmail());
    }

    /** POST /auth/login → 200 + AuthResponse (loose schema: parsed from the raw body map). */
    public AuthResponse login(String email, String password) {
        Object body = guard(() -> api(AuthApi.class, null).apiAuthLoginPost(toAuthRequest(email, password)));
        AuthResponse out = new AuthResponse();
        out.token = textField(body, "token");
        out.tokenType = textField(body, "token_type");
        out.expiresIn = longField(body, "expires_in");
        out.userId = textField(body, "user_id");
        out.email = textField(body, "email");
        return out;
    }

    /** GET /auth/me → MeView (token required). */
    public net.hashbin.cli.model.MeView me(String token) {
        MeView me = guard(() -> api(AuthApi.class, token).apiAuthMeGet());
        net.hashbin.cli.model.MeView view = new net.hashbin.cli.model.MeView();
        view.userId = me.getUserId();
        view.email = me.getEmail();
        return view;
    }

    // ── Generated-client plumbing ───────────────────────────────────────────

    /**
     * A generated {@code *Api} proxy pointed at {@link #apiBase}, with the bearer
     * token attached per-instance via {@link BearerClientHeadersFactory} (a null/blank
     * token adds no header, for anonymous endpoints) and {@link ApiExceptionMapper}
     * so any non-2xx raises the generated {@link net.hashbin.api.ApiException}.
     */
    private <A> A api(Class<A> type, String token) {
        return RestClientBuilder.newBuilder()
                .baseUri(URI.create(serverRoot()))
                .register(new BearerClientHeadersFactory(token))
                .register(new ApiExceptionMapper())
                .build(type);
    }

    /**
     * The REST base URI. The generated interfaces declare {@code @Path("/api")} at
     * class level, so the {@link RestClientBuilder} base must be the <em>server
     * root</em>; strip the trailing {@code /api} that the CLI's {@code apiBase} carries.
     */
    private String serverRoot() {
        return apiBase.endsWith("/api") ? apiBase.substring(0, apiBase.length() - "/api".length()) : apiBase;
    }

    private static StampRequest toStampRequest(Map<String, Object> payload) {
        StampRequest request = new StampRequest();
        request.setHashSha256(string(payload, "hash_sha256"));
        request.setHashMd5(string(payload, "hash_md5"));
        request.setHashSha1(string(payload, "hash_sha1"));
        request.setHashSha512(string(payload, "hash_sha512"));
        Object size = payload.get("file_size");
        request.setFileSize(size instanceof Number n ? n.longValue() : 0L);
        request.setFileName(string(payload, "file_name"));
        return request;
    }

    private static AuthRequest toAuthRequest(String email, String password) {
        AuthRequest request = new AuthRequest();
        request.setEmail(email);
        request.setPassword(password);
        return request;
    }

    private static String string(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v == null ? null : v.toString();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object body) {
        return body instanceof Map ? (Map<String, Object>) body : Map.of();
    }

    private static String textField(Object body, String key) {
        Object v = asMap(body).get(key);
        return v == null ? null : v.toString();
    }

    private static long longField(Object body, String key) {
        Object v = asMap(body).get(key);
        return v instanceof Number n ? n.longValue() : 0L;
    }

    private static TimestampRecord toRecord(TimestampView v) {
        TimestampRecord r = newRecord(v.getId(), v.getHashSha256(), v.getHashMd5(), v.getHashSha1(),
                v.getHashSha512(), v.getFileSize(), v.getCreatedAt(), v.getServerSignature());
        r.fileName = v.getFileName();
        r.userId = v.getUserId();
        return r;
    }

    private static TimestampRecord toRecord(TimestampPublicView v) {
        // Public projection omits file_name and user_id, which stay null (as before).
        return newRecord(v.getId(), v.getHashSha256(), v.getHashMd5(), v.getHashSha1(),
                v.getHashSha512(), v.getFileSize(), v.getCreatedAt(), v.getServerSignature());
    }

    private static TimestampRecord newRecord(String id, String sha256, String md5, String sha1,
            String sha512, Long fileSize, String createdAt, String serverSignature) {
        TimestampRecord r = new TimestampRecord();
        r.id = id;
        r.hashSha256 = sha256;
        r.hashMd5 = md5;
        r.hashSha1 = sha1;
        r.hashSha512 = sha512;
        r.fileSize = fileSize != null ? fileSize : 0L;
        r.createdAt = createdAt;
        r.serverSignature = serverSignature;
        return r;
    }

    private static AuthResponse toAuthResponse(String token, String tokenType, Long expiresIn,
            String userId, String email) {
        AuthResponse out = new AuthResponse();
        out.token = token;
        out.tokenType = tokenType;
        out.expiresIn = expiresIn != null ? expiresIn : 0L;
        out.userId = userId;
        out.email = email;
        return out;
    }

    // ── Error mapping ───────────────────────────────────────────────────────

    /**
     * Runs a generated call and translates its exceptions into the CLI's
     * {@link ApiException}: HTTP errors carry the server's {@code {"error": ...}}
     * message verbatim, transport failures surface as status 0 with the endpoint.
     */
    private <T> T guard(Supplier<T> call) {
        try {
            return call.get();
        } catch (net.hashbin.api.ApiException e) {
            throw translate(e);
        } catch (RuntimeException e) {
            // No HTTP response produced: connection refused / DNS failure / interrupted.
            String cause = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            throw new ApiException(0, "API unreachable (" + apiBase + "): " + cause);
        }
    }

    private ApiException translate(net.hashbin.api.ApiException e) {
        Response response = e.getResponse();
        int status = response != null ? response.getStatus() : 0;
        String body = readEntity(response);
        if (status == 0) {
            String cause = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            return new ApiException(0, "API unreachable (" + apiBase + "): " + cause);
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
