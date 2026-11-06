package net.hashbin.cli.util;

/**
 * Terminal colour helpers, mirroring the original Python CLI: coloured when the target stream is a
 * TTY and {@code NO_COLOR} is unset, plain otherwise. Colour is applied to stdout decisions only at
 * the command layer.
 */
public final class Console {

    private static final boolean COLOR =
            System.getenv("NO_COLOR") == null && System.console() != null;

    private Console() {}

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
        return COLOR ? "\033[" + code + "m" + text + "\033[0m" : text;
    }

    public static void stdOutLn(final String x) {
        System.out.println(x);
    }

    public static void stdOutLn() {
        System.out.println();
    }

    public static void stdOutLn(final Object anything) {
        stdOutLn(anything.toString());
    }

    public static void stdErrLn(final String x) {
        System.err.println(x);
    }

    public static void stdErrLn() {
        System.err.println();
    }

    public static void stdErrLn(final Object anything) {
        stdErrLn(anything.toString());
    }
}
