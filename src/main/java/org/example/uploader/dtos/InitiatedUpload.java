package org.example.uploader.dtos;

import java.util.List;

public record InitiatedUpload(
        String videoId,
        long partSize,
        int partCount,
        List<PresignedPart> presignedParts
) {
}
