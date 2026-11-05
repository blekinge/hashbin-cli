package net.hashbin.cli.client;

import java.io.IOException;
import java.util.List;

import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.client.ClientRequestFilter;

import net.hashbin.cli.config.CliConfig;

/**
 * Attaches the {@code Authorization: Bearer} header <em>per request</em>, only for the
 * endpoints that are authenticated or attributed by token. Anonymous endpoints
 * ({@code /health}, {@code /verify}, {@code /explore}, {@code /auth/login},
 * {@code /auth/register}) are sent without a token, even when one is configured — matching
 * the original CLI, where the bearer was passed only to the calls that use it.
 *
 * <p>This is why the token is applied by a {@code ClientRequestFilter} (which can see the
 * request path) rather than a per-client {@code ClientHeadersFactory} (which cannot, and so
 * would stamp the header on every call). Wired with {@code @RegisterProvider} on the local
 * {@code *Api} sub-interfaces in {@link HashbinClient}.
 */
@Dependent
public class ConditionalBearerFilter implements ClientRequestFilter {

    /**
     * Operation path suffixes that carry the bearer token. Matched against the request
     * path (which the generated {@code @Path("/api")} + method paths make e.g.
     * {@code /api/stamp}), independent of the base URL.
     */
    private static final List<String> BEARER_PATHS = List.of("/stamp", "/my-timestamps", "/auth/me");

    @Inject
    CliConfig config;

    @Override
    public void filter(ClientRequestContext requestContext) throws IOException {
        String token = config.token();
        if (token == null || token.isBlank()) {
            return;
        }
        String path = requestContext.getUri().getPath();
        if (path != null && BEARER_PATHS.stream().anyMatch(path::endsWith)) {
            requestContext.getHeaders().putSingle("Authorization", "Bearer " + token);
        }
    }
}
