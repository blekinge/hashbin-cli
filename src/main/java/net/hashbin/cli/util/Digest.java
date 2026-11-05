package net.hashbin.cli.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Computes MD5, SHA-1, SHA-256 and SHA-512 for a file in a single streaming
 * pass, exactly like the original Python CLI's {@code hash_file}. Hex digests
 * are lowercase so they match the backend's validation patterns.
 */
public final class Digest {

    private static final int CHUNK = 64 * 1024; // 64 KiB, same as the Python CLI

    private Digest() {
    }

    /** Immutable result of hashing one file. All digests are lowercase hex. */
    public record FileHashes(String md5, String sha1, String sha256, String sha512, long size) {
    }

    public static FileHashes hashFile(Path file) throws IOException {
        MessageDigest md5 = newDigest("MD5");
        MessageDigest sha1 = newDigest("SHA-1");
        MessageDigest sha256 = newDigest("SHA-256");
        MessageDigest sha512 = newDigest("SHA-512");

        long size = 0;
        byte[] buf = new byte[CHUNK];
        try (InputStream in = Files.newInputStream(file)) {
            int read;
            while ((read = in.read(buf)) != -1) {
                md5.update(buf, 0, read);
                sha1.update(buf, 0, read);
                sha256.update(buf, 0, read);
                sha512.update(buf, 0, read);
                size += read;
            }
        }
        return new FileHashes(
                toHex(md5.digest()),
                toHex(sha1.digest()),
                toHex(sha256.digest()),
                toHex(sha512.digest()),
                size);
    }

    /** Lowercase hex of a single SHA-256 of some text (used by tests). */
    public static String sha256Hex(String text) {
        return toHex(newDigest("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
    }

    private static MessageDigest newDigest(String algorithm) {
        try {
            return MessageDigest.getInstance(algorithm);
        } catch (NoSuchAlgorithmException e) {
            // All four are guaranteed by the JDK. MD5 can be unavailable only
            // under a FIPS-restricted provider; surface a clear message.
            throw new IllegalStateException("Digest algorithm unavailable: " + algorithm, e);
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder hex = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            hex.append(Character.forDigit((b >> 4) & 0xf, 16));
            hex.append(Character.forDigit(b & 0xf, 16));
        }
        return hex.toString();
    }
}
