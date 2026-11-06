package net.hashbin.cli.store;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Minimal bearer-token cache so {@code login} need not be re-run for every authenticated command.
 * Written on {@code auth login}/{@code auth register}, read as the lowest-priority source of a
 * token (after --token and $HASHBIN_TOKEN).
 *
 * <p>Location: {@code $XDG_CONFIG_HOME/hashbin/credentials.json} or {@code
 * ~/.config/hashbin/credentials.json}. Mode 0600 on POSIX.
 */
public class CredentialsStore {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Path file;

    public CredentialsStore() {
        this(defaultPath());
    }

    public CredentialsStore(Path file) {
        this.file = file;
    }

    private static Path defaultPath() {
        String xdg = System.getenv("XDG_CONFIG_HOME");
        Path base =
                (xdg != null && !xdg.isBlank())
                        ? Path.of(xdg)
                        : Path.of(System.getProperty("user.home"), ".config");
        return base.resolve("hashbin").resolve("credentials.json");
    }

    public Path path() {
        return file;
    }

    /** Stored token, or {@code null} when absent/unreadable (never throws). */
    public String readToken() {
        Map<String, Object> data = read();
        Object token = data.get("token");
        return token instanceof String s && !s.isBlank() ? s : null;
    }

    public void save(Map<String, Object> fields) {
        try {
            Files.createDirectories(file.getParent());
            byte[] json =
                    MAPPER.writerWithDefaultPrettyPrinter()
                            .writeValueAsBytes(new LinkedHashMap<>(fields));
            Files.write(file, json);
            restrictToOwner();
        } catch (IOException e) {
            throw new RuntimeException("could not write " + file + ": " + e.getMessage(), e);
        }
    }

    public void clear() {
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            throw new RuntimeException("could not delete " + file + ": " + e.getMessage(), e);
        }
    }

    private Map<String, Object> read() {
        try {
            if (!Files.isRegularFile(file)) {
                return Map.of();
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> data = MAPPER.readValue(Files.readAllBytes(file), Map.class);
            return data != null ? data : Map.of();
        } catch (IOException malformed) {
            return Map.of();
        }
    }

    private void restrictToOwner() {
        try {
            Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"));
        } catch (UnsupportedOperationException | IOException notPosix) {
            // Windows/other: best effort only. The token is no more exposed than
            // the rest of the user's files there.
        }
    }
}
