package net.hashbin.cli.util;

/**
 * Small display/formatting helpers shared by the commands. Deliberately has no
 * Quarkus/Jackson dependency so it is trivially unit-testable.
 */
public final class Fmt {

    private Fmt() {
    }

    /**
     * Human-readable size, mirroring the original Python CLI's {@code fmt_size}
     * (base-1024, one decimal, no decimals for bytes).
     */
    public static String size(long bytes) {
        if (bytes == 0) {
            return "0 B";
        }
        double n = bytes;
        String[] units = { "B", "KB", "MB", "GB", "TB" };
        int i = 0;
        while (Math.abs(n) >= 1024 && i < units.length - 1) {
            n /= 1024;
            i++;
        }
        if (i == 0) {
            return String.format("%d %s", bytes, units[0]);
        }
        return String.format("%.1f %s", n, units[i]);
    }

    /** True if {@code s} is lowercase hex of a known digest length (32/40/64/128). */
    public static boolean looksLikeHexHash(String s) {
        if (s == null) {
            return false;
        }
        String h = s.toLowerCase();
        int len = h.length();
        if (len != 32 && len != 40 && len != 64 && len != 128) {
            return false;
        }
        for (int i = 0; i < len; i++) {
            char c = h.charAt(i);
            boolean ok = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f');
            if (!ok) {
                return false;
            }
        }
        return true;
    }

    /** First {@code n} characters followed by an ellipsis when longer, else the input. */
    public static String ellipsize(String s, int n) {
        if (s == null) {
            return "";
        }
        return s.length() <= n ? s : s.substring(0, n) + "…";
    }

    /** Null/blank-safe trim that returns {@code null} for blank input. */
    public static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
