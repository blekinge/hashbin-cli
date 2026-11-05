package net.hashbin.cli.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import net.hashbin.cli.model.TimestampRecord;

/**
 * Verifies a returned {@link TimestampRecord}'s {@code server_signature} by
 * recomputing the HMAC the server would have used. This is the CLI's value-add
 * over a plain HTTP call: it proves the record was signed by a holder of the
 * shared {@code HASHBIN_HMAC_SECRET}.
 */
public final class SignatureVerifier {

    private SignatureVerifier() {
    }

    /**
     * @return {@code true} when the recomputed HMAC equals
     *         {@code record.serverSignature} (constant-time comparison).
     */
    public static boolean verify(String secret, TimestampRecord record) {
        String expected = Hmac.sign(secret, record.hashSha256, record.createdAt);
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                record.serverSignature.getBytes(StandardCharsets.UTF_8));
    }
}
