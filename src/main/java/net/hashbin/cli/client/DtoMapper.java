package net.hashbin.cli.client;

import net.hashbin.api.model.AuthRequest;
import net.hashbin.api.model.TimestampPublicView;
import net.hashbin.api.model.TimestampView;
import net.hashbin.cli.model.AuthResponse;
import net.hashbin.cli.model.MeView;
import net.hashbin.cli.model.StampRequest;
import net.hashbin.cli.model.TimestampRecord;

/**
 * Translates between the OpenAPI-generated DTOs ({@code net.hashbin.api.model.*}) and the CLI's
 * stable {@link net.hashbin.cli.model} types.
 *
 * <p>This is the only seam that sees both worlds, so the {@code net.hashbin.cli.model} records
 * carry no dependency on the generated client: a contract regeneration can only ever ripple in here
 * (and in {@link HashbinClient}), never into the command or util layers.
 */
final class DtoMapper {

    private DtoMapper() {}

    // ── generated view → CLI model ──────────────────────────────────────────

    /** Full projection: keeps {@code file_name} and {@code user_id}. */
    static TimestampRecord toRecord(TimestampView v) {
        return TimestampRecord.builder()
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

    /** Public projection: {@code file_name} and {@code user_id} stay {@code null}. */
    static TimestampRecord toRecord(TimestampPublicView v) {
        return TimestampRecord.builder()
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

    static MeView toMeView(net.hashbin.api.model.MeView me) {
        return MeView.builder().userId(me.getUserId()).email(me.getEmail()).build();
    }

    static AuthResponse toAuthResponse(net.hashbin.api.model.AuthResponse r) {
        return AuthResponse.builder()
                .token(r.getToken())
                .tokenType(r.getTokenType())
                .expiresIn(r.getExpiresIn() != null ? r.getExpiresIn() : 0L)
                .userId(r.getUserId())
                .email(r.getEmail())
                .build();
    }

    // ── CLI model → generated request body ──────────────────────────────────

    static net.hashbin.api.model.StampRequest toApiStampRequest(StampRequest payload) {
        net.hashbin.api.model.StampRequest request = new net.hashbin.api.model.StampRequest();
        request.setHashSha256(payload.hashSha256());
        request.setHashMd5(payload.hashMd5());
        request.setHashSha1(payload.hashSha1());
        request.setHashSha512(payload.hashSha512());
        request.setFileSize(payload.fileSize());
        request.setFileName(payload.fileName());
        return request;
    }

    static AuthRequest toApiAuthRequest(String email, String password) {
        AuthRequest request = new AuthRequest();
        request.setEmail(email);
        request.setPassword(password);
        return request;
    }
}
