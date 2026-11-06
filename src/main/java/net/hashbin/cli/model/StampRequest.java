package net.hashbin.cli.model;

import lombok.Builder;

@Builder
public record StampRequest(
        String hashSha256,
        String hashMd5,
        String hashSha1,
        String hashSha512,
        Long fileSize,
        String fileName) {}
