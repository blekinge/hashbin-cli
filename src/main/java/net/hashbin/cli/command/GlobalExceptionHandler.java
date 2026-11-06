package net.hashbin.cli.command;

import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine;

/**
 * Global Picocli handler for exceptions thrown by a command's business logic.
 *
 * <p>Keeps user-facing output to a single clean line on stderr, routes the full stack trace to the
 * logger (which carries the per-run correlation id), and returns a non-zero exit code so a
 * batch/Job run is marked failed. Distinct from a <em>usage</em> error (bad/missing arguments),
 * which Picocli still reports itself with exit code 2.
 *
 * <p>The hashbin commands catch {@code ApiException} themselves (so they can print the server's
 * {@code {"error": ...}} message in the CLI's {@code ✗} style); this handler is the safety net for
 * anything that escapes a command.
 */
@Slf4j
public final class GlobalExceptionHandler implements CommandLine.IExecutionExceptionHandler {

    /** Exit code used for any command that fails at execution time. */
    public static final int EXECUTION_FAILURE = 1;

    @Override
    public int handleExecutionException(
            Exception ex, CommandLine commandLine, CommandLine.ParseResult parseResult) {
        String command = failingCommand(parseResult);
        String message = (ex.getMessage() == null) ? ex.getClass().getName() : ex.getMessage();
        commandLine.getErr().printf("Error: %s%n", message);
        log.error("Command '{}' failed", command, ex);
        return EXECUTION_FAILURE;
    }

    /**
     * Walks to the innermost matched (sub)command so the log names the command that actually ran.
     */
    private static String failingCommand(CommandLine.ParseResult parseResult) {
        CommandLine.ParseResult deepest = parseResult;
        while (deepest.hasSubcommand()) {
            deepest = deepest.subcommand();
        }
        return deepest.commandSpec().name();
    }
}
