package net.hashbin.cli.util;

import net.hashbin.cli.client.ApiException;

/**
 * Single home for terminal output, in three thin layers:
 *
 * <ul>
 *   <li><b>styling</b> — {@code green/red/…} turn a string into a coloured one (coloured when the
 *       target stream is a TTY and {@code NO_COLOR} is unset, plain otherwise; null/blank text is
 *       returned unchanged), mirroring the original Python CLI;
 *   <li><b>writing</b> — {@code stdOutLn/stdErrLn} put a (possibly styled) line on a stream;
 *   <li><b>status vocabulary</b> — {@code ok/warn/error} combine a glyph, colour and stream into
 *       the CLI's conventional status lines.
 * </ul>
 *
 * Exit-code decisions deliberately live in the command layer, not here.
 */
public final class Console {

    private static final boolean COLOR =
            System.getenv("NO_COLOR") == null && System.console() != null;

    private Console() {}

    // ---- styling: pure String -> String ----
    // Colour codes wrap non-blank text only; a null/blank input is returned unchanged (see
    // colorize). Callers can therefore style a possibly-absent value and let the consumer decide.

    public static String green(String t) {
        return colorize("32", t);
    }

    public static String yellow(String t) {
        return colorize("33", t);
    }

    public static String red(String t) {
        return colorize("31", t);
    }

    public static String bold(String t) {
        return colorize("1", t);
    }

    public static String dim(String t) {
        return colorize("2", t);
    }

    public static String cyan(String t) {
        return colorize("36", t);
    }

    private static String colorize(String code, String text) {
        if (text == null || text.isBlank()) {
            return text;
        }
        return COLOR ? "\033[" + code + "m" + text + "\033[0m" : text;
    }

    // ---- writing: raw lines to a stream ----

    public static void stdOutLn(final String x) {
        System.out.println(x);
    }

    public static void stdOutLn() {
        System.out.println();
    }

    public static void stdErrLn(final String x) {
        System.err.println(x);
    }

    public static void stdErrLn() {
        System.err.println();
    }

    // ---- status vocabulary: styled line on the right stream ----

    public static void ok(String message) {
        stdOutLn(green("✓ ") + message);
    }

    public static void warn(String message) {
        stdOutLn(yellow("⚠ ") + message);
    }

    public static void error(String message) {
        stdErrLn(red("✗ ") + message);
    }

    public static void error(Throwable t) {
        error(messageOf(t));
    }

    private static String messageOf(final Throwable t) {
        return t instanceof ApiException
                ? t.getMessage()
                : (t.getMessage() != null ? t.getMessage() : t.toString());
    }
}
