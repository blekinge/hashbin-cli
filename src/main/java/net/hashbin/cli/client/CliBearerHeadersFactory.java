package net.hashbin.cli.client;

import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;

import org.eclipse.microprofile.rest.client.ext.ClientHeadersFactory;

import net.hashbin.cli.config.CliConfig;

/**
 * Adds an {@code Authorization: Bearer <token>} header to every request made by a
 * configured hashbin REST client, taken from the resolved {@link CliConfig}.
 *
 * <p>Wired declaratively with {@code @RegisterClientHeaders} on the local
 * {@code *Api} sub-interfaces (see {@link HashbinClient}), so the token is not a method
 * argument and the generated interfaces need no edits. A null/blank token (not signed in,
 * anonymous endpoints) adds no header.
 */
@Dependent
public class CliBearerHeadersFactory implements ClientHeadersFactory {

    @Inject
    CliConfig config;

    @Override
    public MultivaluedMap<String, String> update(MultivaluedMap<String, String> incomingHeaders,
            MultivaluedMap<String, String> clientOutgoingHeaders) {
        MultivaluedMap<String, String> headers = new MultivaluedHashMap<>();
        String token = config.token();
        if (token != null && !token.isBlank()) {
            headers.add("Authorization", "Bearer " + token);
        }
        return headers;
    }
}
