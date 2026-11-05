package net.hashbin.cli.util;

import java.io.Console;

/**
 * Reads a password from the terminal without echoing, falling back to stdin when
 * no interactive console is attached (e.g. piped input). Returns {@code null}
 * when nothing can be read. Named to avoid clashing with the colour helper.
 */
public final class Passwords {

    private Passwords() {
    }

    public static char[] readPassword(String prompt) {
        Console console = System.console();
        if (console != null) {
            return console.readPassword("%s", prompt);
        }
        // Non-interactive fallback: read a single line from stdin (echoed by the
        // shell, but usable in scripts/CI where a secret is piped in).
        try {
            byte[] line = System.in.readNBytes(1024);
            String s = new String(line, java.nio.charset.StandardCharsets.UTF_8).stripTrailing();
            return s.isEmpty() ? null : s.toCharArray();
        } catch (java.io.IOException e) {
            return null;
        }
    }
}
