package net.hashbin.cli.model;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Builder;
import net.hashbin.api.model.TimestampPublicView;
import net.hashbin.api.model.TimestampView;

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
        String userId) {

    public static TimestampRecord map(TimestampView v) {
        return builder()
                .id(v.getId())
                .hashSha256(v.getHashSha256())
                .hashMd5(v.getHashMd5())
                .hashSha1(v.getHashSha1())
                .hashSha512(v.getHashSha512())
                .fileSize(v.getFileSize() != null ? v.getFileSize() : 0L)
                .createdAt(v.getCreatedAt())
                .serverSignature(v.getServerSignature())
                .fileName(v.getFileName())
                .userId(v.getUserId())
                .build();
    }

    public static TimestampRecord map(TimestampPublicView v) {
        // Public projection omits file_name and user_id, which stay null.
        return builder()
                .id(v.getId())
                .hashSha256(v.getHashSha256())
                .hashMd5(v.getHashMd5())
                .hashSha1(v.getHashSha1())
                .hashSha512(v.getHashSha512())
                .fileSize(v.getFileSize() != null ? v.getFileSize() : 0L)
                .createdAt(v.getCreatedAt())
                .serverSignature(v.getServerSignature())
                .build();
    }
}
