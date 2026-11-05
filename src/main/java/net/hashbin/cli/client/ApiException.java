package net.hashbin.cli.client;

/**
 * A failed API call: non-2xx status, or a transport/parse error. Carries the
 * HTTP {@link #status()} (0 for transport failures) and the human {@code error}
 * message extracted from the body ({@code {"error": "..."}}) when present, so
 * commands can print the server's message verbatim — matching the original API's
 * error contract.
 */
public class ApiException extends RuntimeException {

    private final int status;

    public ApiException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int status() {
        return status;
    }
}
