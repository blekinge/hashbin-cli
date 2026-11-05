package net.hashbin.cli.util;

import net.hashbin.cli.model.TimestampRecord;

/**
 * Renders a {@link TimestampRecord} for humans and, when an HMAC secret is
 * available, recomputes the server signature so the user sees a real trust
 * verdict — not just "the server returned something". This is the whole reason
 * the CLI exposes {@code --hmac-secret}.
 */
public final class TimestampFormat {

    private TimestampFormat() {
    }

    /**
     * Prints one record. {@code heading} is e.g. {@code #1}; the optional
     * {@code fileName}/{@code userId} fields are shown only when present (the
     * public projection omits them).
     */
    public static void print(int heading, TimestampRecord r, String secret) {
        StringBuilder head = new StringBuilder();
        head.append(Console.bold("#" + heading)).append("  ")
                .append(r.createdAt != null ? r.createdAt : "?");
        System.out.println(head);
        System.out.println("      SHA-256:   " + Console.dim(s(r.hashSha256)));
        if (notBlank(r.hashSha1)) {
            System.out.println("      SHA-1:     " + Console.dim(r.hashSha1));
        }
        if (notBlank(r.hashMd5)) {
            System.out.println("      MD5:       " + Console.dim(r.hashMd5));
        }
        if (notBlank(r.hashSha512)) {
            System.out.println("      SHA-512:   " + Console.dim(Fmt.ellipsize(r.hashSha512, 32)));
        }
        System.out.println("      Size:      " + Fmt.size(r.fileSize));
        if (notBlank(r.fileName)) {
            System.out.println("      File name: " + r.fileName);
        }
        System.out.println("      ID:        " + Console.dim(s(r.id)));
        printSignature(r, secret);
        System.out.println();
    }

    private static void printSignature(TimestampRecord r, String secret) {
        if (notBlank(secret) && notBlank(r.hashSha256) && notBlank(r.createdAt) && notBlank(r.serverSignature)) {
            boolean valid = SignatureVerifier.verify(secret, r);
            String verdict = valid
                    ? Console.green("✓ verified")
                    : Console.red("✗ INVALID — signature does not match the secret");
            System.out.println("      Signature: " + Console.dim(Fmt.ellipsize(r.serverSignature, 32)) + "  " + verdict);
        } else {
            System.out.println("      Signature: " + Console.dim(Fmt.ellipsize(s(r.serverSignature), 32))
                    + "  " + Console.dim("(unverified — pass --hmac-secret or set HASHBIN_HMAC_SECRET)"));
        }
    }

    private static String s(String v) {
        return v != null ? v : "-";
    }

    private static boolean notBlank(String v) {
        return v != null && !v.isBlank();
    }
}
