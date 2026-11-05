package net.hashbin.cli.command;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import jakarta.inject.Inject;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import net.hashbin.cli.client.HashbinClient;
import net.hashbin.cli.config.CliConfig;
import net.hashbin.cli.model.TimestampRecord;
import net.hashbin.cli.util.Console;
import net.hashbin.cli.util.Digest;
import net.hashbin.cli.util.Fmt;
import net.hashbin.cli.util.TimestampFormat;

/**
 * {@code hashbin stamp FILE...} — hash each file locally (MD5/SHA-1/SHA-256/
 * SHA-512) and POST the digests to {@code /api/stamp}. When an HMAC secret is
 * available the returned {@code server_signature} is re-verified client-side.
 */
@Command(name = "stamp", description = "Hash files and create signed timestamps")
public class StampCommand implements Callable<Integer> {

    @Inject
    CliConfig config;

    @Parameters(arity = "1..*", paramLabel = "FILE", description = "Files to timestamp")
    List<Path> files;

    @Option(names = "--include-name", description = "Include the file name in the stored record")
    boolean includeName;

    @Override
    public Integer call() {
        String secret = config.hmacSecret();
        String token = config.token();
        HashbinClient client = new HashbinClient(config.apiBase());
        boolean allOk = true;

        for (Path file : files) {
            if (!Files.isRegularFile(file)) {
                System.out.println(Console.red("✗ Not a file: ") + file);
                allOk = false;
                continue;
            }
            try {
                allOk &= stampOne(client, file, secret, token);
            } catch (Exception e) {
                System.out.println(Console.red("✗ ") + file.getFileName() + ": " + e.getMessage());
                allOk = false;
            }
        }
        return allOk ? 0 : 1;
    }

    private boolean stampOne(HashbinClient client, Path file, String secret, String token) throws Exception {
        String name = file.getFileName().toString();
        System.out.println();
        System.out.println(Console.bold("Hashing ") + Console.cyan(name) + " …");

        Digest.FileHashes hashes = Digest.hashFile(file);
        System.out.println("  SHA-256: " + Console.dim(hashes.sha256()));
        System.out.println("  Size:    " + Fmt.size(hashes.size()));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("hash_sha256", hashes.sha256());
        payload.put("hash_md5", hashes.md5());
        payload.put("hash_sha1", hashes.sha1());
        payload.put("hash_sha512", hashes.sha512());
        payload.put("file_size", hashes.size());
        if (includeName) {
            payload.put("file_name", name);
        }

        TimestampRecord created = client.stamp(payload, token);
        System.out.println("  " + Console.green("✓ Timestamped at ") + created.createdAt);
        TimestampFormat.print(1, created, secret);
        return true;
    }
}
