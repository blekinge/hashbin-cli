package net.hashbin.cli.model;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Builder;

/** Response for {@code GET /api/auth/me}. Mirrors the backend's {@code MeView}. */
@RegisterForReflection
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@Builder
public record MeView(String userId, String email) {}
