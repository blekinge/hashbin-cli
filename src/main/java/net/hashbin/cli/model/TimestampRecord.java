package net.hashbin.cli.model;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Builder;

/**
 * One timestamp record as returned by the service. Used for BOTH the full projection ({@code POST
 * /api/stamp}, {@code GET /api/my-timestamps}) and the reduced public projection ({@code GET
 * /api/verify}, {@code GET /api/explore}) — the public projection simply omits {@code file_name}
 * and {@code user_id}, which stay {@code null}.
 */
@RegisterForReflection
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@Builder(toBuilder = true)
public record TimestampRecord(
        String id,
        String hashSha256,
        String hashMd5,
        String hashSha1,
        String hashSha512,
        long fileSize,
        String fileName,
        String createdAt,
        String serverSignature,
        String userId) {}
