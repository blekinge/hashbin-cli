package net.hashbin.cli.config;

import lombok.Builder;
import lombok.Value;

/**
 * The resolved, application-wide command-line configuration.
 *
 * <p>Built once per run by {@link CliConfigProducer} from the running command's parsed
 * options (with env-var and stored-credential fallbacks), then injected into commands
 * and services. This is the bridge that decouples command classes from Picocli's
 * {@code @ParentCommand} wiring: anything that needs the API base URL, the HMAC secret
 * or the bearer token asks CDI for {@link CliConfig} instead of walking the command tree.
 *
 * <p>Produced as a normal-scoped ({@code @ApplicationScoped}) bean, so injection sites
 * receive a lazy proxy and the producer runs only when a value is actually read — i.e. at
 * command execution, once the {@code ParseResult} it derives from is available. Reading it
 * eagerly at bean-creation time would cycle with the Picocli command build.
 *
 * <p>lombok {@code @Value} (immutable, fluent accessors per {@code lombok.config}) +
 * {@code @Builder}. The secret fields are excluded from the generated {@code toString} so a
 * token/secret can never leak into a log line.
 *
 * @param apiBase    resolved API base URL (never null; falls back to the default)
 * @param hmacSecret HMAC secret for client-side signature verification, or {@code null}
 * @param token      bearer token for authenticated calls, or {@code null}
 */
@Value
@Builder
public class CliConfig {

    String apiBase;

    @lombok.ToString.Exclude
    String hmacSecret;

    @lombok.ToString.Exclude
    String token;
}
