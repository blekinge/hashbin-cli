package net.hashbin.cli.model;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * One timestamp record as returned by the service. Used for BOTH the full
 * projection ({@code POST /api/stamp}, {@code GET /api/my-timestamps}) and the
 * reduced public projection ({@code GET /api/verify}, {@code GET /api/explore})
 * — the public projection simply omits {@code file_name} and {@code user_id},
 * which deserialize to {@code null}.
 *
 * <p>{@link RegisterForReflection} + snake_case naming are required for the
 * native image (a JVM-mode-only reflection pass otherwise serializes to an
 * empty object — the same gotcha the backend documents).
 */
@RegisterForReflection
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class TimestampRecord {

    public String id;
    public String hashSha256;
    public String hashMd5;
    public String hashSha1;
    public String hashSha512;
    public long fileSize;
    public String fileName;
    public String createdAt;
    public String serverSignature;
    public String userId;
}
