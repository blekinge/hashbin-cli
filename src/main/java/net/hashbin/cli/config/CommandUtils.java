package net.hashbin.cli.config;

import java.util.Arrays;
import picocli.CommandLine;

/**
 * Helpers for reading parsed command-line values out of the Quarkus Picocli {@link
 * CommandLine.ParseResult} context object — the bridge between the command line and CDI (see {@link
 * CliConfigProducer}).
 */
public final class CommandUtils {

    private CommandUtils() {}

    /**
     * Reads the value of a <em>top-level</em> (root command) option by name.
     *
     * <p>The global options ({@code --api-base}, {@code --hmac-secret}, {@code --token}) live on
     * the root {@code Main} command, so their values are taken from the root command spec
     * regardless of which subcommand ran. Returns the value Picocli bound (the flag, or its
     * declared default {@code null} when not supplied) — callers apply the env/default fallbacks on
     * top.
     */
    public static <T> T rootOption(
            final CommandLine.ParseResult parseResult, final String optionName) {
        return parseResult.commandSpec().args().stream()
                .filter(CommandLine.Model.OptionSpec.class::isInstance)
                .map(CommandLine.Model.OptionSpec.class::cast)
                .filter(arg -> Arrays.asList(arg.names()).contains(optionName))
                .findAny()
                .<T>map(CommandLine.Model.ArgSpec::getValue)
                .orElse(null);
    }
}
