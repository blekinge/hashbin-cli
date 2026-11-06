package net.hashbin.cli.util;

import net.hashbin.cli.model.TimestampRecord;

/**
 * Renders a {@link TimestampRecord} for humans and, when an HMAC secret is available, recomputes
 * the server signature so the user sees a real trust verdict — not just "the server returned
 * something". This is the whole reason the CLI exposes {@code --hmac-secret}.
 */
public final class TimestampFormat {

    /**
     * A row is a 6-space indent plus an 11-char left-justified label, so values align at column 17.
     */
    private static final String ROW = "      %-11s%s";

    private TimestampFormat() {}

    /**
     * Prints one record. {@code heading} is e.g. {@code #1}. Optional fields — the non-SHA-256
     * digests and {@code fileName} — are shown only when present (the public projection omits
     * them); an absent value stays blank through {@link Console} and {@link #row} drops the line.
     */
    public static void print(int heading, TimestampRecord r, String secret) {
        Console.stdOutLn(
                Console.bold("#" + heading) + "  " + (r.createdAt() != null ? r.createdAt() : "?"));
        row("SHA-256:", Console.dim(s(r.hashSha256())));
        row("SHA-1:", Console.dim(r.hashSha1()));
        row("MD5:", Console.dim(r.hashMd5()));
        row("SHA-512:", Console.dim(Fmt.ellipsize(r.hashSha512(), 32)));
        row("Size:", Fmt.size(r.fileSize()));
        row("File name:", r.fileName());
        row("ID:", Console.dim(s(r.id())));
        printSignature(r, secret);
        Console.stdOutLn();
    }

    private static void printSignature(TimestampRecord r, String secret) {
        String signature = Console.dim(Fmt.ellipsize(s(r.serverSignature()), 32));
        String verdict;
        if (verifiable(r, secret)) {
            verdict =
                    SignatureVerifier.verify(secret, r)
                            ? Console.green("✓ verified")
                            : Console.red("✗ INVALID — signature does not match the secret");
        } else {
            verdict = Console.dim("(unverified — pass --hmac-secret or set HASHBIN_HMAC_SECRET)");
        }
        row("Signature:", signature + "  " + verdict);
    }

    /** The signature is only meaningful when we have the secret and every signed field. */
    private static boolean verifiable(TimestampRecord r, String secret) {
        return notBlank(secret)
                && notBlank(r.hashSha256())
                && notBlank(r.createdAt())
                && notBlank(r.serverSignature());
    }

    /**
     * Prints one indented, label-aligned row. A blank {@code value} — which {@link Console}'s
     * styling helpers pass through unchanged for an absent field — prints nothing at all, so
     * optional fields need no guard at the call site.
     */
    private static void row(String label, String value) {
        if (notBlank(value)) {
            Console.stdOutLn(String.format(ROW, label, value));
        }
    }

    private static String s(String v) {
        return v != null ? v : "-";
    }

    private static boolean notBlank(String v) {
        return v != null && !v.isBlank();
    }
}
