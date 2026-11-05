package net.hashbin.cli.command;

import java.util.List;
import java.util.concurrent.Callable;

import jakarta.inject.Inject;

import picocli.CommandLine.Command;

import net.hashbin.cli.client.ApiException;
import net.hashbin.cli.client.HashbinClient;
import net.hashbin.cli.config.CliConfig;
import net.hashbin.cli.model.TimestampRecord;
import net.hashbin.cli.util.Cli;
import net.hashbin.cli.util.Console;
import net.hashbin.cli.util.TimestampFormat;

/** {@code hashbin my-timestamps} — GET /api/my-timestamps (bearer token required). */
@Command(name = "my-timestamps", description = "List your own timestamps (requires login or --token)")
public class MyTimestampsCommand implements Callable<Integer> {

    @Inject
    HashbinClient client;

    @Inject
    CliConfig config;

    @Override
    public Integer call() {
        String token = config.token();
        if (token == null) {
            System.out.println(Console.red("✗ ") + "No token. Run `hashbin auth login` or pass --token / HASHBIN_TOKEN.");
            return 1;
        }
        try {
            List<TimestampRecord> records = client.myTimestamps(token);
            if (records.isEmpty()) {
                Cli.warn("No timestamps recorded for this account.");
                return 0;
            }
            Cli.ok("Found " + records.size() + " timestamp(s):\n");
            int i = 1;
            for (TimestampRecord r : records) {
                TimestampFormat.print(i++, r, config.hmacSecret());
            }
            return 0;
        } catch (ApiException e) {
            return Cli.fail(e);
        }
    }
}
