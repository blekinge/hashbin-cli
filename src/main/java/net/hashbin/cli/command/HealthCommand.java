package net.hashbin.cli.command;

import java.util.concurrent.Callable;

import com.fasterxml.jackson.databind.JsonNode;

import picocli.CommandLine.Command;
import picocli.CommandLine.ParentCommand;

import net.hashbin.cli.client.ApiException;
import net.hashbin.cli.util.Cli;
import net.hashbin.cli.util.Console;

/** {@code hashbin health} — GET /api/health. */
@Command(name = "health", description = "Check API availability")
public class HealthCommand implements Callable<Integer> {

    @ParentCommand
    HashbinCommand root;

    @Override
    public Integer call() {
        try {
            JsonNode node = root.client().health();
            if ("ok".equals(node.path("status").asText(null))) {
                Cli.ok("hashbin API is healthy  " + Console.dim(root.resolveApiBase()));
                return 0;
            }
            System.out.println(Console.red("✗ ") + "unexpected response: " + node);
            return 1;
        } catch (ApiException e) {
            return Cli.fail(e);
        }
    }
}
