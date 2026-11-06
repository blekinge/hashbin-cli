package net.hashbin.cli.command;

import io.quarkus.picocli.runtime.annotations.TopCommand;
import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;
import jakarta.inject.Inject;
import java.util.Arrays;
import picocli.AutoComplete;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/**
 * Quarkus command line entry point and top-level {@code hashbin} Picocli command.
 *
 * <p>Owns the three global options every subcommand shares — API base URL, HMAC secret and bearer
 * token (plus a global {@code --verbose} flag). These fields are populated by Picocli and surfaced
 * to the rest of the app as an injectable {@link net.hashbin.cli.config.CliConfig} by {@link
 * net.hashbin.cli.config.CliConfigProducer}; subcommands inject that bean rather than walking the
 * command tree.
 */
@QuarkusMain
@TopCommand
@Command(
        name = "hashbin",
        mixinStandardHelpOptions = true,
        versionProvider = VersionProvider.class,
        description = "Command line client for the hashbin.net trusted file-timestamping service.",
        subcommands = {
            AutoComplete.GenerateCompletion.class,
            HealthCommand.class,
            StampCommand.class,
            VerifyCommand.class,
            MyTimestampsCommand.class,
            AuthCommand.class,
            ExploreCommand.class,
            ChangelogCommand.class,
            LicenseCommand.class,
            CommandLine.HelpCommand.class,
        })
public class Main implements QuarkusApplication {

    /** Picocli factory used to create command instances through CDI. */
    @Inject CommandLine.IFactory factory;

    @Option(
            names = "--api-base",
            paramLabel = "URL",
            defaultValue = "${env:HASHBIN_API_BASE:-https://hashbin.net/api}",
            description = "API base URL (env HASHBIN_API_BASE; default https://hashbin.net/api).")
    String apiBase;

    @Option(
            names = "--hmac-secret",
            paramLabel = "SECRET",
            description =
                    "HMAC secret used to verify server signatures (env HASHBIN_HMAC_SECRET). "
                            + "When unset, signature verification is skipped.")
    String hmacSecret;

    @Option(
            names = "--token",
            paramLabel = "JWT",
            description =
                    "Bearer token for authenticated stamps and /my-timestamps "
                            + "(env HASHBIN_TOKEN; else the stored login token).")
    String token;

    /**
     * Global {@code --verbose} flag (available to every subcommand). Its effect is applied in
     * {@link #main(String...)} by raising the application's own log level to {@code DEBUG} before
     * the runtime boots; the field exists so Picocli accepts the flag and lists it in {@code
     * --help}.
     */
    @Option(
            names = {"-v", "--verbose"},
            scope = CommandLine.ScopeType.INHERIT,
            description = "Enable debug logging for this run.")
    boolean verbose;

    @Spec CommandLine.Model.CommandSpec spec;

    @Override
    public int run(String... args) {
        return new CommandLine(this, factory)
                // Route any exception a command lets escape through the global handler:
                // clean stderr message + logged stack trace + non-zero exit code.
                .setExecutionExceptionHandler(new GlobalExceptionHandler())
                .execute(args);
    }

    /** Invoked only when no subcommand is given: print usage. */
    public void run() {
        spec.commandLine().usage(System.err);
    }

    /**
     * Main method that starts Quarkus.
     *
     * <p>{@code --verbose} is handled here rather than inside a command: verbosity is a run-wide,
     * startup-time concern, so the flag is translated into Quarkus's own log-level configuration
     * before the runtime boots (raising only the application's own package to DEBUG; see {@code
     * application.properties}).
     */
    public static void main(String... args) {
        if (Arrays.asList(args).contains("--verbose") || Arrays.asList(args).contains("-v")) {
            System.setProperty("app.log.level", "DEBUG");
        }
        Quarkus.run(Main.class, args);
    }
}
