package net.hashbin.cli.command;

import jakarta.inject.Inject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;
import net.hashbin.cli.client.HashbinClient;
import net.hashbin.cli.config.CliConfig;
import net.hashbin.cli.model.StampRequest;
import net.hashbin.cli.model.TimestampRecord;
import net.hashbin.cli.util.Console;
import net.hashbin.cli.util.Digest;
import net.hashbin.cli.util.Fmt;
import net.hashbin.cli.util.TimestampFormat;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * {@code hashbin stamp FILE...} — hash each file locally (MD5/SHA-1/SHA-256/ SHA-512) and POST the
 * digests to {@code /api/stamp}. When an HMAC secret is available the returned {@code
 * server_signature} is re-verified client-side.
 */
@Command(name = "stamp", description = "Hash files and create signed timestamps")
public class StampCommand implements Callable<Integer> {

    @Inject HashbinClient client;

    @Inject CliConfig config;

    @Parameters(arity = "1..*", paramLabel = "FILE", description = "Files to timestamp")
    List<Path> files;

    @Option(names = "--include-name", description = "Include the file name in the stored record")
    boolean includeName;

    @Override
    public Integer call() {
        String secret = config.hmacSecret();
        boolean allOk = true;

        for (Path file : files) {
            if (!Files.isRegularFile(file)) {
                Console.error("Not a file: " + file);
                allOk = false;
                continue;
            }
            try {
                allOk &= stampOne(file, secret);
            } catch (Exception e) {
                Console.error(file.getFileName() + ": " + e.getMessage());
                allOk = false;
            }
        }
        return allOk ? 0 : 1;
    }

    private boolean stampOne(Path file, String secret) throws Exception {
        String name = file.getFileName().toString();
        Console.stdOutLn();
        Console.stdOutLn(Console.bold("Hashing ") + Console.cyan(name) + " …");

        Digest.FileHashes hashes = Digest.hashFile(file);
        Console.stdOutLn("  SHA-256: " + Console.dim(hashes.sha256()));
        Console.stdOutLn("  Size:    " + Fmt.size(hashes.size()));

        var payload =
                StampRequest.builder()
                        .hashSha256(hashes.sha256())
                        .hashMd5(hashes.md5())
                        .hashSha1(hashes.sha1())
                        .hashSha512(hashes.sha512())
                        .fileSize(hashes.size())
                        .fileName(includeName ? name : null)
                        .build();

        TimestampRecord created = client.stamp(payload);
        Console.stdOutLn("  " + Console.green("✓ Timestamped at ") + created.createdAt());
        TimestampFormat.print(1, created, secret);
        return true;
    }
}
