package net.hashbin.cli.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Digest must match known vectors and hash the whole file (multi-chunk safe). */
class DigestTest {

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
}
