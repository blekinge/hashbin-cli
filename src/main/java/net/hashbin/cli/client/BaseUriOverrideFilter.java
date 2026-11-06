package net.hashbin.cli.client;

import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.client.ClientRequestFilter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import net.hashbin.cli.config.CliConfig;

/**
 * Re-points each request at the base URL supplied on the command line, so the CLI's {@code
 * --api-base} / {@code $HASHBIN_API_BASE} is honoured even though the injected {@code @RestClient}
 * binds its base URL from config at container startup.
 *
 * <p>Wired with {@code @RegisterProvider} on the local {@code *Api} sub-interfaces (see {@link
 * HashbinClient}). The {@code @RestClient} proxy builds a request against the placeholder {@code
 * quarkus.rest-client.hashbin.url}; this filter rewrites only the <em>origin</em>
 * (scheme/host/port, plus any non-{@code /api} base path) to the command's server root, leaving the
 * operation path and query the generated {@code @Path} annotations produced. The path is already
 * correct because the generated interfaces declare {@code @Path("/api")}; the CLI base URL's {@code
 * /api} suffix is therefore stripped here (as the facade did before).
 *
 * <p>{@link CliConfig} is read lazily inside {@link #filter} — i.e. at request time, once the
 * {@code ParseResult} it derives from is available — never during the command build.
 */
@Dependent
public class BaseUriOverrideFilter implements ClientRequestFilter {

    @Inject CliConfig config;

    @Override
    public void filter(ClientRequestContext requestContext) throws IOException {
        URI root = serverRoot();
        URI current = requestContext.getUri();
        String basePath = root.getPath() == null ? "" : root.getPath();
        try {
            requestContext.setUri(
                    new URI(
                            root.getScheme(),
                            root.getUserInfo(),
                            root.getHost(),
                            root.getPort(),
                            basePath + current.getPath(),
                            current.getQuery(),
                            current.getFragment()));
        } catch (URISyntaxException e) {
            throw new UncheckedIOException(new IOException("Invalid --api-base: " + root, e));
        }
    }

    /**
     * Command server root: the resolved base URL with any trailing slash and {@code /api} suffix
     * removed.
     */
    private URI serverRoot() {
        String base = config.apiBase();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        if (base.endsWith("/api")) {
            base = base.substring(0, base.length() - "/api".length());
        }
        return URI.create(base);
    }
}
