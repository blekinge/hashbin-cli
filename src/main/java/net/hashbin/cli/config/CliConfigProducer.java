package net.hashbin.cli.config;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import net.hashbin.cli.store.CredentialsStore;
import picocli.CommandLine;

/**
 * CDI producer for the application-wide {@link CliConfig}.
 *
 * <p>This is the bridge between the command line and dependency injection: Quarkus Picocli exposes
 * the running command's {@link CommandLine.ParseResult} as an injectable context object, so
 * otherwise non-injectable global options can be surfaced as a configuration bean for commands to
 * inject. Values are taken from the root {@code Main} command and each falls back to its
 * environment variable and default/stored value when the flag is not supplied.
 *
 * <p>Resolution precedence (highest first), kept in plain Java rather than MicroProfile Config so
 * the whole tree behaves identically under {@code @QuarkusMainTest}:
 *
 * <ul>
 *   <li>API base: {@code --api-base} → {@code $HASHBIN_API_BASE} → {@code https://hashbin.net/api},
 *       resolved by Picocli's {@code defaultValue} interpolation, so the producer just reads the
 *       bound value
 *   <li>HMAC secret: {@code --hmac-secret} → {@code $HASHBIN_HMAC_SECRET} → (none)
 *   <li>Token: {@code --token} → {@code $HASHBIN_TOKEN} → stored login credentials
 * </ul>
 *
 * <p>The bean is {@code @ApplicationScoped}: injection sites get a lazy client proxy, so this
 * producer runs only when a command first reads a value (at execution time), after the {@code
 * ParseResult} is resolved — never during the Picocli command build, which would cycle.
 */
@ApplicationScoped
public class CliConfigProducer {

    @Produces
    @ApplicationScoped
    public CliConfig create(CommandLine.ParseResult parseResult) {
        // --api-base already carries its env/default value: Picocli interpolates
        // ${env:HASHBIN_API_BASE:-https://hashbin.net/api} into the bound option.
        String apiBase = CommandUtils.rootOption(parseResult, "--api-base");
        String hmacSecret =
                firstNonBlank(
                        CommandUtils.rootOption(parseResult, "--hmac-secret"),
                        System.getenv("HASHBIN_HMAC_SECRET"),
                        null);
        String token =
                firstNonBlank(
                        CommandUtils.rootOption(parseResult, "--token"),
                        System.getenv("HASHBIN_TOKEN"),
                        new CredentialsStore().readToken());
        return CliConfig.builder().apiBase(apiBase).hmacSecret(hmacSecret).token(token).build();
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
