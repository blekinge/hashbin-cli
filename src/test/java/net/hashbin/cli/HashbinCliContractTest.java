package net.hashbin.cli;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.quarkus.test.junit.main.Launch;
import io.quarkus.test.junit.main.LaunchResult;
import io.quarkus.test.junit.main.QuarkusMainTest;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import net.hashbin.cli.util.Hmac;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Drives the real {@code Main} command tree end-to-end (arg parsing → {@code HashbinClient} →
 * MicroProfile REST client → JSON binding → client-side signature verification → exit codes)
 * against a throwaway HTTP server that speaks the hashbin wire contract. This is the self-contained
 * stand-in for the live backend: it proves the CLI is a correct wire client, including the HMAC
 * trust check, with no database or container.
 *
 * <p>Uses the template's {@code @QuarkusMainTest} + {@code @Launch} harness (in-JVM), so the
 * Quarkus runtime — and therefore the REST client — is fully booted. Because {@code @Launch}
 * arguments are compile-time constants, the stub binds a fixed port and the {@code stamp} case
 * hashes a committed fixture file rather than a {@code @TempDir}.
 *
 * <p>Run against the real backend too via {@code scripts/contract-check.sh}.
 */
@QuarkusMainTest
class HashbinCliContractTest {

    private static final String SECRET = "dev-only-insecure-secret-change-me";
    private static final String CREATED_AT = "2026-11-05T08:17:11.717Z";
    // A 64-char SHA-256-shaped value as a compile-time constant (@Launch args must be constants).
    private static final String SHA =
            "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";
    private static final String BASE = "http://127.0.0.1:18080/api";

    /** A port that requires privilege and is never bound → guarantees a connection-refused. */
    private static final String UNREACHABLE = "http://127.0.0.1:1/api";

    private static final String TOKEN = "test-bearer-token";
    private static final ObjectMapper M = new ObjectMapper();

    // Captured by the stub handlers to assert per-request bearer behaviour.
    private static final AtomicReference<String> VERIFY_AUTH = new AtomicReference<>();
    private static final AtomicReference<String> STAMP_AUTH = new AtomicReference<>();

    private static HttpServer server;

    @BeforeAll
    static void startStub() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 18080), 0);
        server.createContext("/api/health", HashbinCliContractTest::health);
        server.createContext("/api/verify", HashbinCliContractTest::verify);
        server.createContext("/api/stamp", HashbinCliContractTest::stamp);
        server.createContext("/api/explore", HashbinCliContractTest::exploreFails);
        server.start();
    }

    @AfterAll
    static void stopStub() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    @Launch({"--api-base", BASE, "health"})
    void healthHealthy(LaunchResult result) {
        assertThat(result.exitCode()).isZero();
        assertThat(result.getOutput()).contains("healthy");
    }

    @Test
    @Launch(
            value = {"--api-base", UNREACHABLE, "health"},
            exitCode = 1)
    void healthUnreachableMapsError(LaunchResult result) {
        // Point at a closed port → transport error, exit 1.
        assertThat(result.getErrorOutput()).contains("✗");
    }

    @Test
    @Launch({"--api-base", BASE, "--hmac-secret", SECRET, "verify", SHA})
    void verifyValidatesSignature(LaunchResult result) {
        assertThat(result.exitCode()).isZero();
        assertThat(result.getOutput())
                .contains("Found 1 timestamp")
                .contains("verified"); // recomputed HMAC matched
    }

    @Test
    @Launch({
        "--api-base",
        BASE,
        "--hmac-secret",
        SECRET,
        "stamp",
        "--include-name",
        "src/test/resources/fixtures/doc.txt"
    })
    void stampHashesFileAndVerifiesResponse(LaunchResult result) {
        assertThat(result.exitCode()).isZero();
        assertThat(result.getOutput()).contains("Timestamped at").contains("verified");
    }

    @Test
    @Launch(
            value = {"--api-base", BASE, "explore"},
            exitCode = 1)
    void serverErrorIsSurfaced(LaunchResult result) {
        // /api/explore returns 500 + {"error": ...}; the client must map the status to a
        // non-zero exit and print the server's own message.
        assertThat(result.getErrorOutput()).contains("database unavailable");
    }

    @Test
    @Launch({"--api-base", BASE, "verify", SHA})
    void anonymousVerifySendsNoBearer(LaunchResult result) {
        // No token on the command line: the anonymous /verify call carries no Authorization.
        assertThat(result.exitCode()).isZero();
        assertThat(VERIFY_AUTH.get()).isNull();
    }

    @Test
    @Launch({"--api-base", BASE, "--token", TOKEN, "stamp", "src/test/resources/fixtures/doc.txt"})
    void stampSendsBearerWhenSignedIn(LaunchResult result) {
        // With --token, the attributed /stamp call carries the bearer header.
        assertThat(result.exitCode()).isZero();
        assertThat(STAMP_AUTH.get()).isEqualTo("Bearer " + TOKEN);
    }

    // ── stub handlers ────────────────────────────────────────────────────────

    private static void exploreFails(HttpExchange ex) throws java.io.IOException {
        respond(ex, 500, "{\"error\":\"database unavailable\"}");
    }

    private static void health(HttpExchange ex) throws java.io.IOException {
        respond(ex, 200, "{\"status\":\"ok\"}");
    }

    private static void verify(HttpExchange ex) throws java.io.IOException {
        VERIFY_AUTH.set(ex.getRequestHeaders().getFirst("Authorization"));
        String sha = SHA;
        String sig = Hmac.sign(SECRET, sha, CREATED_AT);
        String json =
                "[{\"id\":\"11111111-1111-1111-1111-111111111111\",\"hash_sha256\":\""
                        + sha
                        + "\",\"file_size\":123,\"created_at\":\""
                        + CREATED_AT
                        + "\",\"server_signature\":\""
                        + sig
                        + "\"}]";
        respond(ex, 200, json);
    }

    /** Acts like the real server: reads the digest, signs "{sha}:{created_at}". */
    private static void stamp(HttpExchange ex) throws java.io.IOException {
        STAMP_AUTH.set(ex.getRequestHeaders().getFirst("Authorization"));
        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        JsonNode req = M.readTree(body);
        String sha = req.path("hash_sha256").asText("");
        if (!sha.matches("^[a-f0-9]{64}$")) {
            respond(ex, 400, "{\"error\":\"Invalid SHA-256 hash\"}");
            return;
        }
        String sig = Hmac.sign(SECRET, sha, CREATED_AT);
        String fileName = req.path("file_name").asText(null);
        String json =
                "{\"id\":\"22222222-2222-2222-2222-222222222222\",\"hash_sha256\":\""
                        + sha
                        + "\",\"hash_md5\":\""
                        + "c".repeat(32)
                        + "\",\"hash_sha1\":\""
                        + "d".repeat(40)
                        + "\",\"hash_sha512\":\""
                        + "e".repeat(128)
                        + "\",\"file_size\":13"
                        + ",\"file_name\":"
                        + (fileName == null ? "null" : "\"" + fileName + "\"")
                        + ",\"created_at\":\""
                        + CREATED_AT
                        + "\",\"server_signature\":\""
                        + sig
                        + "\",\"user_id\":null}";
        respond(ex, 200, json);
    }

    private static void respond(HttpExchange ex, int status, String body)
            throws java.io.IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }
}
