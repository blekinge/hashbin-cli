package net.hashbin.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import net.hashbin.cli.command.HashbinCommand;
import net.hashbin.cli.util.Hmac;
import picocli.CommandLine;

/**
 * Drives the real {@link HashbinCommand} tree (arg parsing → {@code HashbinClient}
 * → JSON binding → client-side signature verification → exit codes) against a
 * throwaway HTTP server that speaks the hashbin wire contract. This is the
 * self-contained stand-in for the live backend: it proves the CLI is a correct
 * wire client, including the HMAC trust check, with no database or container.
 *
 * <p>Run against the real backend too via {@code scripts/contract-check.sh}.
 */
class HashbinCliContractTest {

    private static final String SECRET = "dev-only-insecure-secret-change-me";
    private static final String CREATED_AT = "2026-11-05T08:17:11.717Z";
    private static final ObjectMapper M = new ObjectMapper();

    private HttpServer server;
    private String apiBase;

    @BeforeEach
    void startStub() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/health", this::health);
        server.createContext("/api/verify", this::verify);
        server.createContext("/api/stamp", this::stamp);
        server.createContext("/api/explore", this::exploreFails);
        server.start();
        apiBase = "http://127.0.0.1:" + server.getAddress().getPort() + "/api";
    }

    @AfterEach
    void stopStub() {
        server.stop(0);
    }

    @Test
    void healthHealthy() {
        Result r = run("--api-base", apiBase, "health");
        assertEquals(0, r.exit);
        assertTrue(r.out.contains("healthy"), r.out);
    }

    @Test
    void healthUnreachableMapsError() {
        // Point at a closed port → transport error, exit 1.
        Result r = run("--api-base", "http://127.0.0.1:1/api", "health");
        assertEquals(1, r.exit);
        assertTrue(r.out.contains("✗"), r.out);
    }

    @Test
    void verifyValidatesSignature() {
        Result r = run("--api-base", apiBase, "--hmac-secret", SECRET, "verify", "b".repeat(64));
        assertEquals(0, r.exit);
        assertTrue(r.out.contains("Found 1 timestamp"), r.out);
        assertTrue(r.out.contains("verified"), r.out); // recomputed HMAC matched
    }

    @Test
    void stampHashesFileAndVerifiesResponse(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("doc.txt");
        Files.write(file, "hello hashbin".getBytes(StandardCharsets.UTF_8));

        Result r = run("--api-base", apiBase, "--hmac-secret", SECRET, "stamp", "--include-name", file.toString());
        assertEquals(0, r.exit, r.out);
        assertTrue(r.out.contains("Timestamped at"), r.out);
        assertTrue(r.out.contains("verified"), r.out);
    }

    @Test
    void serverErrorIsSurfaced() {
        // /api/explore returns 500 + {"error": ...}; the client must map the status
        // to a non-zero exit and print the server's own message.
        Result r = run("--api-base", apiBase, "explore");
        assertEquals(1, r.exit);
        assertTrue(r.out.contains("database unavailable"), r.out);
    }

    // ── command runner ───────────────────────────────────────────────────────

    private record Result(int exit, String out) {
    }

    private Result run(String... args) {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        java.io.PrintStream original = System.out;
        System.setOut(new java.io.PrintStream(buf, true, StandardCharsets.UTF_8));
        try {
            int exit = new CommandLine(new HashbinCommand()).execute(args);
            return new Result(exit, buf.toString(StandardCharsets.UTF_8));
        } finally {
            System.setOut(original);
        }
    }

    private void exploreFails(HttpExchange ex) throws java.io.IOException {
        respond(ex, 500, "{\"error\":\"database unavailable\"}");
    }

    // ── stub handlers ────────────────────────────────────────────────────────

    private void health(HttpExchange ex) throws java.io.IOException {
        respond(ex, 200, "{\"status\":\"ok\"}");
    }

    private void verify(HttpExchange ex) throws java.io.IOException {
        String sha = "b".repeat(64);
        String sig = Hmac.sign(SECRET, sha, CREATED_AT);
        String json = "[{\"id\":\"11111111-1111-1111-1111-111111111111\",\"hash_sha256\":\"" + sha
                + "\",\"file_size\":123,\"created_at\":\"" + CREATED_AT + "\",\"server_signature\":\"" + sig + "\"}]";
        respond(ex, 200, json);
    }

    /** Acts like the real server: reads the digest, signs "{sha}:{created_at}". */
    private void stamp(HttpExchange ex) throws java.io.IOException {
        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        JsonNode req = M.readTree(body);
        String sha = req.path("hash_sha256").asText("");
        if (!sha.matches("^[a-f0-9]{64}$")) {
            respond(ex, 400, "{\"error\":\"Invalid SHA-256 hash\"}");
            return;
        }
        String sig = Hmac.sign(SECRET, sha, CREATED_AT);
        String fileName = req.path("file_name").asText(null);
        String json = "{\"id\":\"22222222-2222-2222-2222-222222222222\",\"hash_sha256\":\"" + sha
                + "\",\"hash_md5\":\"" + "c".repeat(32) + "\",\"hash_sha1\":\"" + "d".repeat(40)
                + "\",\"hash_sha512\":\"" + "e".repeat(128) + "\",\"file_size\":13"
                + ",\"file_name\":" + (fileName == null ? "null" : "\"" + fileName + "\"")
                + ",\"created_at\":\"" + CREATED_AT + "\",\"server_signature\":\"" + sig
                + "\",\"user_id\":null}";
        respond(ex, 200, json);
    }

    private void respond(HttpExchange ex, int status, String body) throws java.io.IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }
}
