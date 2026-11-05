package net.hashbin.cli.config;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

import picocli.CommandLine;

import net.hashbin.cli.store.CredentialsStore;

/**
 * CDI producer for the application-wide {@link CliConfig}.
 *
 * <p>This is the bridge between the command line and dependency injection: Quarkus Picocli
 * exposes the running command's {@link CommandLine.ParseResult} as an injectable context
 * object, so otherwise non-injectable global options can be surfaced as a configuration bean
 * for commands to inject. Values are taken from the root {@code Main} command and each falls
 * back to its environment variable and default/stored value when the flag is not supplied.
 *
 * <p>Resolution precedence (highest first), kept in plain Java rather than MicroProfile
 * Config so the whole tree behaves identically under {@code @QuarkusMainTest}:
 * <ul>
 *   <li>API base: {@code --api-base} → {@code $HASHBIN_API_BASE} → {@value #DEFAULT_API_BASE}</li>
 *   <li>HMAC secret: {@code --hmac-secret} → {@code $HASHBIN_HMAC_SECRET} → (none)</li>
 *   <li>Token: {@code --token} → {@code $HASHBIN_TOKEN} → stored login credentials</li>
 * </ul>
 *
 * <p>The bean is {@code @ApplicationScoped}: injection sites get a lazy client proxy, so this
 * producer runs only when a command first reads a value (at execution time), after the
 * {@code ParseResult} is resolved — never during the Picocli command build, which would cycle.
 */
@ApplicationScoped
public class CliConfigProducer {

    static final String DEFAULT_API_BASE = "http://localhost:8080/api";

    @Produces
    @ApplicationScoped
    public CliConfig create(CommandLine.ParseResult parseResult) {
        String apiBase = firstNonBlank(
                CommandUtils.rootOption(parseResult, "--api-base"),
                System.getenv("HASHBIN_API_BASE"),
                DEFAULT_API_BASE);
        String hmacSecret = firstNonBlank(
                CommandUtils.rootOption(parseResult, "--hmac-secret"),
                System.getenv("HASHBIN_HMAC_SECRET"),
                null);
        String token = firstNonBlank(
                CommandUtils.rootOption(parseResult, "--token"),
                System.getenv("HASHBIN_TOKEN"),
                new CredentialsStore().readToken());
        return CliConfig.builder()
                .apiBase(apiBase)
                .hmacSecret(hmacSecret)
                .token(token)
                .build();
    }

    private static String firstNonBlank(String... candidates) {
        for (String c : candidates) {
            if (c != null && !c.isBlank()) {
                return c;
            }
        }
        return null;
    }
}
