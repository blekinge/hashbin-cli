package net.hashbin.cli.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Random;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Digest must match known vectors and hash the whole payload, across chunk boundaries. */
class DigestTest {

    /**
     * Larger than {@code InputStream.transferTo}'s 8 KiB buffer and deliberately not a multiple of
     * it, so the streaming loop covers full chunks plus a short final read for all four digests.
     */
    private static final int BIG = 1_000_000 + 3;

    @Test
    void hashesKnownVector(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("abc.txt");
        Files.write(file, "abc".getBytes(StandardCharsets.UTF_8));

        Digest.FileHashes h = Digest.hashFile(file);
        assertEquals(
                "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", h.sha256());
        assertEquals("900150983cd24fb0d6963f7d28e17f72", h.md5());
        assertEquals("a9993e364706816aba3e25717850c26c9cd0d89d", h.sha1());
        assertEquals(3, h.size());
    }

    @Test
    void hashesLargeStreamInOnePass() throws Exception {
        byte[] data = deterministicBytes(BIG);

        // Reference: the JDK's one-shot digests over the whole array.
        Digest.FileHashes expected =
                new Digest.FileHashes(
                        hex("MD5", data),
                        hex("SHA-1", data),
                        hex("SHA-256", data),
                        hex("SHA-512", data),
                        data.length);

        assertEquals(expected, Digest.hash(new ByteArrayInputStream(data)));
    }

    @Test
    void hashFileMatchesStreamForLargePayload(@TempDir Path dir) throws Exception {
        byte[] data = deterministicBytes(BIG);
        Path file = dir.resolve("big.bin");
        Files.write(file, data);

        assertEquals(Digest.hash(new ByteArrayInputStream(data)), Digest.hashFile(file));
    }

    private static byte[] deterministicBytes(int size) {
        byte[] data = new byte[size];
        new Random(20261106L).nextBytes(data);
        return data;
    }

    private static String hex(String algorithm, byte[] data) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance(algorithm).digest(data));
    }
}
