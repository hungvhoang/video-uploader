package org.example.uploader.dtos;

import org.example.uploader.models.Video;

import java.time.Instant;

public record VideoView(String id, String fileName, String contentType, long size, Instant createdAt) {
    public static VideoView from(Video video) {
        return new VideoView(video.getId(), video.getOriginalFilename(), video.getContentType(),
                video.getSize(), video.getCreatedAt());
    }
}
