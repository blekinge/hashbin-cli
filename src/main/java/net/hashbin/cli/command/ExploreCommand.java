package net.hashbin.cli.command;

import java.util.List;
import java.util.concurrent.Callable;

import jakarta.inject.Inject;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import net.hashbin.cli.client.ApiException;
import net.hashbin.cli.client.HashbinClient;
import net.hashbin.cli.config.CliConfig;
import net.hashbin.cli.model.TimestampRecord;
import net.hashbin.cli.util.Cli;
import net.hashbin.cli.util.TimestampFormat;

/** {@code hashbin explore} — GET /api/explore (recent public timestamps). */
@Command(name = "explore", description = "List recent public timestamps")
public class ExploreCommand implements Callable<Integer> {

    @Inject
    CliConfig config;

    @Option(names = "--limit", paramLabel = "N", description = "Page size, 1-100 (default 20)")
    Integer limit;

    @Option(names = "--offset", paramLabel = "N", description = "Rows to skip (default 0)")
    Integer offset;

    @Override
    public Integer call() {
        try {
            List<TimestampRecord> records = new HashbinClient(config.apiBase()).explore(limit, offset);
            if (records.isEmpty()) {
                Cli.warn("No public timestamps.");
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
