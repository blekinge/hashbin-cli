package net.hashbin.cli.model;

import lombok.Builder;

@Builder
public record StampRequest(
        String hashSha256,
        String hashMd5,
        String hashSha1,
        String hashSha512,
        Long fileSize,
        String fileName) {

    public static net.hashbin.api.model.StampRequest toStampRequest(StampRequest payload) {
        net.hashbin.api.model.StampRequest request = new net.hashbin.api.model.StampRequest();
        request.setHashSha256(payload.hashSha256());
        request.setHashMd5(payload.hashMd5());
        request.setHashSha1(payload.hashSha1());
        request.setHashSha512(payload.hashSha512());
        request.setFileSize(payload.fileSize());
        request.setFileName(payload.fileName());
        return request;
    }
}
