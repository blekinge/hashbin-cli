package net.hashbin.cli.model;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * Response for {@code POST /api/auth/register} and {@code POST /api/auth/login}.
 * Mirrors the backend's {@code AuthResponse} (snake_case on the wire).
 */
@RegisterForReflection
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class AuthResponse {

    public String token;
    public String tokenType;
    public long expiresIn;
    public String userId;
    public String email;
}
