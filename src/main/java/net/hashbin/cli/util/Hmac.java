package net.hashbin.cli.util;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Client-side re-implementation of the backend's signature scheme
 * ({@code net.hashbin.service.HmacSigner} in the sibling hashbin-quarkus repo):
 * {@code HMAC-SHA256(secret, "{hash_sha256}:{created_at}")}, lowercase hex.
 *
 * <p>The CLI does NOT sign requests — the server signs each record and returns
 * {@code server_signature}. Holding the shared secret lets the client
 * independently confirm that a timestamp was really produced by a server that
 * knows that secret, which is the trust property the service provides. This
 * class exists so that check works with no compile-time dependency on the
 * backend: the contract (message format + HMAC-SHA256) is all that is shared.
 */
public final class Hmac {

    private Hmac() {
    }

    /** The canonical message the server signs for a timestamp record. */
    public static String canonicalMessage(String hashSha256, String createdAtIso) {
        return hashSha256 + ":" + createdAtIso;
    }

    public static String sign(String secret, String hashSha256, String createdAtIso) {
        return hmacSha256Hex(secret, canonicalMessage(hashSha256, createdAtIso));
    }

    /** Pure HMAC-SHA256 over {@code secret}/{@code message}, lowercase hex. */
    public static String hmacSha256Hex(String secret, String message) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] out = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));

            StringBuilder hex = new StringBuilder(out.length * 2);
            for (byte b : out) {
                hex.append(Character.forDigit((b >> 4) & 0xf, 16));
                hex.append(Character.forDigit(b & 0xf, 16));
            }
            return hex.toString();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC signing failed", e);
        }
    }
}
