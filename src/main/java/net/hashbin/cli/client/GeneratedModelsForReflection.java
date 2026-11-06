package net.hashbin.cli.client;

import io.quarkus.runtime.annotations.RegisterForReflection;
import net.hashbin.api.model.AuthRequest;
import net.hashbin.api.model.AuthResponse;
import net.hashbin.api.model.MeView;
import net.hashbin.api.model.StampRequest;
import net.hashbin.api.model.TimestampPublicView;
import net.hashbin.api.model.TimestampView;

/**
 * Registers the {@code hashbin-api} generated model classes for GraalVM reflection.
 *
 * <p>The MicroProfile REST Client binds request and response bodies with Jackson via these POJOs
 * (declared on the generated {@code net.hashbin.api.*Api} interfaces). In JVM mode runtime
 * reflection makes that work; in a native image they must be registered explicitly or they
 * (de)serialize to empty objects — the exact gotcha the backend's DTOs guard against with their own
 * {@link RegisterForReflection}.
 *
 * <p>This holder keeps that concern in one place at the CLI's coupling boundary; the generated
 * sources in the {@code hashbin-api} dependency are never hand-edited.
 */
@RegisterForReflection(
        targets = {
            AuthRequest.class,
            AuthResponse.class,
            MeView.class,
            StampRequest.class,
            TimestampPublicView.class,
            TimestampView.class,
        })
final class GeneratedModelsForReflection {

    private GeneratedModelsForReflection() {}
}
