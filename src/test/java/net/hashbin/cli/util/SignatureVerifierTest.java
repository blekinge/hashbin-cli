package net.hashbin.cli.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.hashbin.cli.model.TimestampRecord;
import org.junit.jupiter.api.Test;

class SignatureVerifierTest {

    private static final String SECRET = "dev-only-insecure-secret-change-me";

    private TimestampRecord record() {
        String hashSha256 = "a".repeat(64);
        String createdAt = "2026-11-05T08:17:11.717Z";
        return TimestampRecord.builder()
                .hashSha256(hashSha256)
                .createdAt(createdAt)
                .serverSignature(
                        // as the server would sign
                        Hmac.sign(SECRET, hashSha256, createdAt))
                .build();
    }

    @Test
    void validSignatureVerifies() {
        assertTrue(SignatureVerifier.verify(SECRET, record()));
    }

    @Test
    void tamperedCreatedAtFails() {
        TimestampRecord r = record().toBuilder().createdAt("2020-01-01T00:00:00.000Z").build();
        assertFalse(SignatureVerifier.verify(SECRET, r));
    }

    @Test
    void wrongSecretFails() {
        assertFalse(SignatureVerifier.verify("other-secret", record()));
    }
}
