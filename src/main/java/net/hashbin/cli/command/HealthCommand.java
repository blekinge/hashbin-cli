package net.hashbin.cli.command;

import jakarta.inject.Inject;
import java.util.concurrent.Callable;
import net.hashbin.cli.client.ApiException;
import net.hashbin.cli.client.HashbinClient;
import net.hashbin.cli.config.CliConfig;
import net.hashbin.cli.util.Console;
import picocli.CommandLine.Command;

/** {@code hashbin health} — GET /api/health. */
@Command(name = "health", description = "Check API availability")
public class HealthCommand implements Callable<Integer> {

    @Inject HashbinClient client;

    @Inject CliConfig config;

    @Override
    public Integer call() {
        try {
            String status = client.health();
            if ("ok".equals(status)) {
                Console.ok("hashbin API is healthy  " + Console.dim(config.apiBase()));
                return 0;
            }
            Console.error("unexpected response: status=" + status);
            return 1;
        } catch (ApiException e) {
            Console.error(e);
            return 1;
        }
    }
}
