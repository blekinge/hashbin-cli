package net.hashbin.cli.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FmtTest {

    @Test
    void sizeIsBase1024() {
        assertEquals("0 B", Fmt.size(0));
        assertEquals("512 B", Fmt.size(512));
        assertEquals("1.0 KB", Fmt.size(1024));
        assertEquals("513.0 KB", Fmt.size(1024 + 512 * 1024));
        assertEquals("1.5 MB", Fmt.size(1536L * 1024));
        assertEquals("1.0 GB", Fmt.size(1024L * 1024 * 1024));
    }

    @Test
    void hexHashDetection() {
        assertTrue(Fmt.looksLikeHexHash("a".repeat(64)));
        assertTrue(Fmt.looksLikeHexHash("0123456789abcdef".repeat(8))); // 128
        assertFalse(Fmt.looksLikeHexHash("a".repeat(63))); // wrong length
        assertFalse(Fmt.looksLikeHexHash("g".repeat(64))); // not hex
        assertFalse(Fmt.looksLikeHexHash("/some/file.txt"));
    }
}
