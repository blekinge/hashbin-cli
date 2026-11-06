package net.hashbin.cli.command;

import jakarta.inject.Inject;
import java.util.List;
import java.util.concurrent.Callable;
import net.hashbin.cli.client.ApiException;
import net.hashbin.cli.client.HashbinClient;
import net.hashbin.cli.config.CliConfig;
import net.hashbin.cli.model.TimestampRecord;
import net.hashbin.cli.util.Console;
import net.hashbin.cli.util.TimestampFormat;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/** {@code hashbin explore} — GET /api/explore (recent public timestamps). */
@Command(name = "explore", description = "List recent public timestamps")
public class ExploreCommand implements Callable<Integer> {

    @Inject HashbinClient client;

    @Inject CliConfig config;

    @Option(
            names = "--limit",
            paramLabel = "N",
            defaultValue = "20",
            description = "Page size, 1-100 (default 20)")
    Integer limit;

    @Option(
            names = "--offset",
            paramLabel = "N",
            defaultValue = "0",
            description = "Rows to skip (default 0)")
    Integer offset;

    @Override
    public Integer call() {
        try {
            List<TimestampRecord> records = client.explore(limit, offset);
            if (records.isEmpty()) {
                Console.warn("No public timestamps.");
                return 0;
            }
            Console.ok("Found " + records.size() + " timestamp(s):\n");
            int i = 1;
            for (TimestampRecord record : records) {
                TimestampFormat.print(i++, record, config.hmacSecret());
            }
            return 0;
        } catch (ApiException e) {
            Console.error(e);
            return 1;
        }
    }
}
