package net.hashbin.cli.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import net.hashbin.cli.model.TimestampRecord;

class SignatureVerifierTest {

    private static final String SECRET = "dev-only-insecure-secret-change-me";

    private TimestampRecord record() {
        TimestampRecord r = new TimestampRecord();
        r.hashSha256 = "a".repeat(64);
        r.createdAt = "2026-11-05T08:17:11.717Z";
        r.serverSignature = Hmac.sign(SECRET, r.hashSha256, r.createdAt); // as the server would sign
        return r;
    }

    @Test
    void validSignatureVerifies() {
        assertTrue(SignatureVerifier.verify(SECRET, record()));
    }

    @Test
    void tamperedCreatedAtFails() {
        TimestampRecord r = record();
        r.createdAt = "2020-01-01T00:00:00.000Z";
        assertFalse(SignatureVerifier.verify(SECRET, r));
    }

    @Test
    void wrongSecretFails() {
        assertFalse(SignatureVerifier.verify("other-secret", record()));
    }
}
