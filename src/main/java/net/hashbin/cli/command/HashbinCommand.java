package net.hashbin.cli.command;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

import io.quarkus.picocli.runtime.annotations.TopCommand;

import net.hashbin.cli.client.HashbinClient;
import net.hashbin.cli.store.CredentialsStore;

/**
 * Top-level {@code hashbin} command. Owns the three global options that every
 * subcommand shares — API base URL, HMAC secret (for signature verification)
 * and bearer token — and exposes them to subcommands via {@link ParentCommand}.
 *
 * <p>Value resolution is done here in plain Java (flag → env var → default)
 * rather than through MicroProfile Config, so the whole command tree can be
 * driven in unit tests with {@code new CommandLine(new HashbinCommand())} and no
 * CDI container.
 */
@TopCommand
@Command(
        name = "hashbin",
        mixinStandardHelpOptions = true,
        version = "hashbin-cli 1.0.0",
        description = "Command line client for the hashbin.net trusted file-timestamping service.",
        subcommands = {
                HealthCommand.class,
                StampCommand.class,
                VerifyCommand.class,
                MyTimestampsCommand.class,
                AuthCommand.class,
                ExploreCommand.class,
        })
public class HashbinCommand implements Runnable {

    static final String DEFAULT_API_BASE = "http://localhost:8080/api";

    @Option(names = "--api-base", paramLabel = "URL",
            description = "API base URL, e.g. https://hashbin.net/api "
                    + "(env HASHBIN_API_BASE; default " + DEFAULT_API_BASE + ").")
    String apiBase;

    @Option(names = "--hmac-secret", paramLabel = "SECRET",
            description = "HMAC secret used to verify server signatures (env HASHBIN_HMAC_SECRET). "
                    + "When unset, signature verification is skipped.")
    String hmacSecret;

    @Option(names = "--token", paramLabel = "JWT",
            description = "Bearer token for authenticated stamps and /my-timestamps "
                    + "(env HASHBIN_TOKEN; else the stored login token).")
    String token;

    @Spec
    picocli.CommandLine.Model.CommandSpec spec;

    @Override
    public void run() {
        // Invoked only when no subcommand is given.
        spec.commandLine().usage(System.err);
    }

    // ── Shared collaborators / resolution (used by subcommands) ──────────────

    HashbinClient client() {
        return new HashbinClient(resolveApiBase());
    }

    String resolveApiBase() {
        return firstNonBlank(apiBase, System.getenv("HASHBIN_API_BASE"), DEFAULT_API_BASE);
    }

    /** Resolved HMAC secret, or {@code null} when not provided. */
    String resolveSecret() {
        return firstNonBlank(hmacSecret, System.getenv("HASHBIN_HMAC_SECRET"), null);
    }

    /** Resolved bearer token: flag → env → stored credentials → {@code null}. */
    String resolveToken() {
        String fromFlagOrEnv = firstNonBlank(token, System.getenv("HASHBIN_TOKEN"), null);
        return fromFlagOrEnv != null ? fromFlagOrEnv : new CredentialsStore().readToken();
    }

    private static String firstNonBlank(String... candidates) {
        for (String c : candidates) {
            if (c != null && !c.isBlank()) {
                return c;
            }
        }
        return null;
    }
}
