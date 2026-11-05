package net.hashbin.cli.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Proves the client HMAC matches an independent, authoritative source (RFC 4231
 * test case 2 for HMAC-SHA256). This is what lets the CLI trust that it
 * recomputes the same digest the backend's {@code HmacSigner} produces.
 */
class HmacTest {

    @Test
    void hmacSha256MatchesRfc4231TestCase2() {
        String secret = "Jefe";
        String message = "what do ya want for nothing?";
        String expected = "5bdcc146bf60754e6a042426089575c75a003f089d2739839dec58b964ec3843";
        assertEquals(expected, Hmac.hmacSha256Hex(secret, message));
    }

    @Test
    void canonicalMessageIsHashColonCreatedAt() {
        // The exact scheme the backend signs: "{hash_sha256}:{created_at}".
        String secret = "s3cr3t";
        String sha = "a".repeat(64);
        String createdAt = "2026-11-05T08:17:11.717Z";
        assertEquals(
                Hmac.hmacSha256Hex(secret, sha + ":" + createdAt),
                Hmac.sign(secret, sha, createdAt));
    }
}
