package net.hashbin.cli.util;

import net.hashbin.cli.client.ApiException;

/** Tiny shared CLI output helpers: a standard failure line + exit code. */
public final class Cli {

    private Cli() {}

    /** Prints a {@code ✗ message} line to stdout and returns exit code 1. */
    public static int fail(Throwable t) {
        Console.stdErrLn(Console.red("✗ ") + getThrowableMessage(t));
        return 1;
    }

    private static String getThrowableMessage(final Throwable t) {
        return t instanceof ApiException
                ? t.getMessage()
                : (t.getMessage() != null ? t.getMessage() : t.toString());
    }

    public static void ok(String message) {
        Console.stdOutLn(Console.green("✓ ") + message);
    }

    public static void warn(String message) {
        Console.stdOutLn(Console.yellow("⚠ ") + message);
    }
}
