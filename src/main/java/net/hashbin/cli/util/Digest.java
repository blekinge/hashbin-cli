package net.hashbin.cli.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Computes MD5, SHA-1, SHA-256 and SHA-512 for a file in a single streaming pass, exactly like the
 * original Python CLI's {@code hash_file}. Hex digests are lowercase so they match the backend's
 * validation patterns.
 */
public final class Digest {

    private static final HexFormat HEX = HexFormat.of();

    private Digest() {}

    /** Immutable result of hashing one file. All digests are lowercase hex. */
    public record FileHashes(String md5, String sha1, String sha256, String sha512, long size) {}

    public static FileHashes hashFile(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            return hash(in);
        }
    }

    /**
     * Reads {@code in} to its end in a single pass, feeding every byte into all four digests, and
     * returns the digests with the number of bytes read. Does not close {@code in}: the caller that
     * owns the stream keeps responsibility for closing it. Package-private so tests can stream
     * large payloads without touching disk.
     */
    static FileHashes hash(InputStream in) throws IOException {
        MessageDigest md5 = newDigest("MD5");
        MessageDigest sha1 = newDigest("SHA-1");
        MessageDigest sha256 = newDigest("SHA-256");
        MessageDigest sha512 = newDigest("SHA-512");

        // transferTo drives the reads and discards the content, returning the total byte count.
        long size =
                new ChecksumInputStream(in, md5, sha1, sha256, sha512)
                        .transferTo(OutputStream.nullOutputStream());

        return new FileHashes(
                HEX.formatHex(md5.digest()),
                HEX.formatHex(sha1.digest()),
                HEX.formatHex(sha256.digest()),
                HEX.formatHex(sha512.digest()),
                size);
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
}
