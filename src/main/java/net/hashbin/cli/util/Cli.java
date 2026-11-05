package net.hashbin.cli.util;

import net.hashbin.cli.client.ApiException;

/** Tiny shared CLI output helpers: a standard failure line + exit code. */
public final class Cli {

    private Cli() {
    }

    /** Prints a {@code ✗ message} line to stdout and returns exit code 1. */
    public static int fail(Throwable t) {
        String message = t instanceof ApiException ? t.getMessage()
                : (t.getMessage() != null ? t.getMessage() : t.toString());
        System.out.println(Console.red("✗ ") + message);
        return 1;
    }

    public static void ok(String message) {
        System.out.println(Console.green("✓ ") + message);
    }

    public static void warn(String message) {
        System.out.println(Console.yellow("⚠ ") + message);
    }
}
