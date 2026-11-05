package net.hashbin.cli.command;

import java.util.concurrent.Callable;

import jakarta.inject.Inject;

import picocli.CommandLine.Command;

import net.hashbin.cli.client.ApiException;
import net.hashbin.cli.client.HashbinClient;
import net.hashbin.cli.config.CliConfig;
import net.hashbin.cli.util.Cli;
import net.hashbin.cli.util.Console;

/** {@code hashbin health} — GET /api/health. */
@Command(name = "health", description = "Check API availability")
public class HealthCommand implements Callable<Integer> {

    @Inject
    HashbinClient client;

    @Inject
    CliConfig config;

    @Override
    public Integer call() {
        try {
            String status = client.health();
            if ("ok".equals(status)) {
                Cli.ok("hashbin API is healthy  " + Console.dim(config.apiBase()));
                return 0;
            }
            System.out.println(Console.red("✗ ") + "unexpected response: status=" + status);
            return 1;
        } catch (ApiException e) {
            return Cli.fail(e);
        }
    }
}
