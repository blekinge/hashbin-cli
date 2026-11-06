package net.hashbin.cli.model;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Builder;

/**
 * Response for {@code POST /api/auth/register} and {@code POST /api/auth/login}. Mirrors the
 * backend's {@code AuthResponse} (snake_case on the wire).
 */
@RegisterForReflection
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@Builder
public record AuthResponse(
        String token, String tokenType, long expiresIn, String userId, String email) {

    public static AuthResponse map(final net.hashbin.api.model.AuthResponse r) {
        return builder()
                .token(r.getToken())
                .tokenType(r.getTokenType())
                .expiresIn(r.getExpiresIn() != null ? r.getExpiresIn() : 0L)
                .userId(r.getUserId())
                .email(r.getEmail())
                .build();
    }
}
