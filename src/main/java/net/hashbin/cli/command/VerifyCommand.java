package net.hashbin.cli.command;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;

import jakarta.inject.Inject;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import net.hashbin.cli.client.ApiException;
import net.hashbin.cli.client.HashbinClient;
import net.hashbin.cli.config.CliConfig;
import net.hashbin.cli.model.TimestampRecord;
import net.hashbin.cli.util.Cli;
import net.hashbin.cli.util.Console;
import net.hashbin.cli.util.Digest;
import net.hashbin.cli.util.Fmt;
import net.hashbin.cli.util.TimestampFormat;

/**
 * {@code hashbin verify FILE_OR_HASH} — GET /api/verify. A file argument is
 * hashed (SHA-256) first; a hex argument is looked up directly. The algorithm is
 * auto-detected by length unless given.
 */
@Command(name = "verify", description = "Verify a file or hash against existing timestamps")
public class VerifyCommand implements Callable<Integer> {

    @Inject
    CliConfig config;

    @Parameters(arity = "1", paramLabel = "FILE_OR_HASH", description = "File path or hex hash")
    String target;

    @Option(names = "--algorithm", defaultValue = "auto",
            description = "auto|md5|sha1|sha256|sha512 (default: ${DEFAULT-VALUE})")
    String algorithm;

    @Override
    public Integer call() {
        String secret = config.hmacSecret();
        String hash;
        String algo = algorithm;

        if (isFile(target)) {
            try {
                Digest.FileHashes hashes = Digest.hashFile(Path.of(target));
                hash = hashes.sha256();
                algo = "sha256";
                System.out.println(Console.bold("Hashing ") + Console.cyan(Path.of(target).getFileName().toString()));
                System.out.println("  SHA-256: " + Console.dim(hash));
                System.out.println("  Size:    " + Fmt.size(hashes.size()));
            } catch (Exception e) {
                return Cli.fail(e);
            }
        } else if (Fmt.looksLikeHexHash(target)) {
            hash = target.toLowerCase();
        } else {
            System.out.println(Console.red("✗ '") + target + Console.red("' is not a valid hex hash or existing file"));
            return 1;
        }

        try {
            List<TimestampRecord> records = new HashbinClient(config.apiBase()).verify(hash, algo);
            if (records.isEmpty()) {
                Cli.warn("No timestamps found for this hash.");
                return 0;
            }
            Cli.ok("Found " + records.size() + " timestamp(s):\n");
            int i = 1;
            for (TimestampRecord r : records) {
                TimestampFormat.print(i++, r, secret);
            }
            return 0;
        } catch (ApiException e) {
            return Cli.fail(e);
        }
    }

    private static boolean isFile(String s) {
        try {
            return Files.isRegularFile(Path.of(s));
        } catch (java.nio.file.InvalidPathException badName) {
            return false; // a hex "hash" is never a valid path on this OS anyway
        }
    }
}
